/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc
package classfile

import scala.collection.mutable
import mutable.{ ArrayBuffer, Buffer }
import scala.annotation.tailrec
import scala.util.control.NonFatal
import java.io.File
import java.net.URL

import xsbti.{ VirtualFile, VirtualFileRef }
import xsbti.api.DependencyContext
import xsbti.api.DependencyContext._
import sbt.io.IO
import sbt.util.Logger
import xsbti.compile.Output
import java.nio.file.Path

private[sbt] object JavaAnalyze {
  def apply[T](
      newClasses: Seq[Path],
      sources: Seq[VirtualFile],
      log: Logger,
      output: Output,
      finalJarOutput: Option[Path]
  )(
      analysis: xsbti.AnalysisCallback,
      loader: ClassLoader,
      readAPI: (VirtualFileRef, Seq[Class[?]]) => Set[(String, String)],
      readClassfileAPI: (VirtualFileRef, Seq[(String, ClassFile)]) => Unit = (_, _) => (),
      // sbt/zinc#145: extra member-ref edges for inlined `static final` constants that javac erases
      // from the bytecode, recovered from the attributed AST. Keyed `fromBinaryName -> onBinaryNames`.
      constantDeps: Map[String, Set[String]] = Map.empty
  ): Unit = {
    val sourceMap = sources
      .toSet[VirtualFile]
      .groupBy(_.name)
    // For performance reasons, precompute these as they are static throughout this analysis
    val singleOutputOrNull: Path = output.getSingleOutputAsPath.orElse(null)
    val directOutputJarOrNull: Path = JarUtils.getOutputJar(output).getOrElse(null)
    val mappedOutputJarOrNull: Path = finalJarOutput.getOrElse(null)

    def load(tpe: String, errMsg: => Option[String]): Option[Class[?]] =
      try Some(Class.forName(tpe, false, loader))
      catch {
        case e: Throwable => errMsg.foreach(msg => log.warn(msg + " : " + e.toString)); None
      }

    def remapClassFile(classFile: Path) =
      if (directOutputJarOrNull != null && classFile.getFileSystem.provider.getScheme == "jar")
        // convert to the class-in-jar path format that zinc uses. we make an assumption here that
        // if we've got a jar-based path, it's referring to a class in the output jar.
        JarUtils
          .ClassInJar(directOutputJarOrNull, classFile.getRoot.relativize(classFile).toString)
          .toPath
      else if (singleOutputOrNull != null && mappedOutputJarOrNull != null)
        resolveFinalClassFile(classFile, singleOutputOrNull, mappedOutputJarOrNull, log)
      else
        classFile

    val sourceToClassFiles = mutable.HashMap[VirtualFile, Buffer[ClassFile]](
      sources.map(vf => vf -> new ArrayBuffer[ClassFile])*
    )

    val binaryClassNameToLoadedClass = new mutable.HashMap[String, Class[?]]
    // Source (canonical) names of classes that couldn't be reflectively loaded (sbt/zinc#837),
    // derived from the classfile so their products and member-ref dependencies are still recorded.
    val binaryClassNameToSourceName = new mutable.HashMap[String, String]

    val classfilesCache = mutable.Map.empty[String, Path]
    // sbt/zinc#149: referenced classes whose classfile could not be located, keyed to the classes
    // that reached them; reported once per missing class after the analysis. Java analysis is
    // deliberately best-effort: a compile that javac accepted never fails here — whatever cannot
    // be recovered is reported and the corresponding dependency edges are simply absent.
    val missingClassReferrers = mutable.Map.empty[String, mutable.Set[String]]

    // parse class files and assign classes to sources.  This must be done before dependencies, since the information comes
    // as class->class dependencies that must be mapped back to source->class dependencies using the source+class assignment
    for {
      newClass <- newClasses
      classFile = Parser(newClass, log)
      _ <- classFile.sourceFile orElse guessSourceName(newClass.getFileName.toString)
      source <- guessSourcePath(sourceMap, classFile, log)
      binaryClassName = classFile.className
      // module-info.class is not a real class; it has no API or dependencies to analyze, and is
      // deliberately excluded (do not let it fall into the unloadable-class fallback below).
      if !binaryClassName.endsWith("module-info")
    } {
      val finalClassFile: Path = remapClassFile(newClass)
      load(
        binaryClassName,
        Some(
          "While analyzing " + binaryClassName +
            ": the class could not be loaded; falling back to classfile-based analysis"
        )
      ) match {
        case Some(loadedClass) =>
          binaryClassNameToLoadedClass.update(binaryClassName, loadedClass)
          loadEnclosingClass(loadedClass) match {
            case Some(className) =>
              analysis.generatedNonLocalClass(source, finalClassFile, binaryClassName, className)
            case None => analysis.generatedLocalClass(source, finalClassFile)
          }
        case None =>
          // sbt/zinc#837: the class can't be reflectively loaded (e.g. its superclass is in a
          // module not exported to the unnamed module). Fall back to the classfile so the product
          // and member-ref dependencies are still recorded; the API and inheritance dependencies
          // need the loaded Class and are skipped.
          canonicalClassName(classFile) match {
            case Some(className) =>
              analysis.generatedNonLocalClass(source, finalClassFile, binaryClassName, className)
              binaryClassNameToSourceName.update(binaryClassName, className)
            case None => analysis.generatedLocalClass(source, finalClassFile)
          }
      }

      sourceToClassFiles(source) += classFile
    }

    // sbt/zinc#148: since JDK 8, javac requires every transitive superclass/superinterface of a
    // referenced type on the classpath, even though the referencing classfile names only the type
    // itself. We record that ancestry as member-ref deps of the referencing class, chiefly for
    // build tools that minimize the classpath from Zinc's analysis. Parents are read from classfile
    // bytes (never by loading the class) and memoized.
    val classFileByBinaryName: Map[String, ClassFile] =
      sourceToClassFiles.valuesIterator.flatten.map(cf => cf.className -> cf).toMap
    val resourceUrls = mutable.Map.empty[String, Option[URL]]
    val resolvedClassFiles = mutable.Map.empty[String, Option[ClassFile]]
    val transitiveAncestorsCache = mutable.Map.empty[String, Set[String]]

    def resourceUrl(binaryName: String): Option[URL] =
      resourceUrls.getOrElseUpdate(
        binaryName,
        Option(loader.getResource(classNameToClassFile(binaryName)))
      )

    // Whether a class is "platform" (on the JDK runtime, so never tracked and not walked) is
    // decided by ORIGIN, not package name: many javax.* APIs (servlet, JAX-RS, JAXB) ship as
    // ordinary classpath jars and must stay tracked. A class is platform iff its classfile is
    // served from the runtime image (`jrt:`); `java.*` is reserved (always the JDK) so we skip the
    // lookup. Compiled classes are never platform.
    def isPlatformClass(binaryName: String): Boolean =
      binaryName.startsWith("java.") ||
        (!classFileByBinaryName.contains(binaryName) &&
          resourceUrl(binaryName).exists(_.getProtocol == "jrt"))

    // sbt/zinc#149: when a reflection failure names a class that is genuinely absent from the
    // analysis classpath, report it like any other unlocatable referenced class.
    def recordMissingClass(e: Throwable, referrer: String): Unit =
      for {
        missing <- missingClassName(e)
        if !isPlatformClass(missing) && resourceUrl(missing).isEmpty
      } missingClassReferrers.getOrElseUpdate(missing, mutable.Set.empty) += referrer

    def resolveClassFile(binaryName: String): Option[ClassFile] =
      classFileByBinaryName
        .get(binaryName)
        .orElse(
          resolvedClassFiles.getOrElseUpdate(
            binaryName,
            resourceUrl(binaryName).flatMap { url =>
              try Some(Parser(url, log))
              catch {
                case NonFatal(e) =>
                  log.debug(s"[zinc] sbt/zinc#148: couldn't read parents of $binaryName ($e)")
                  None
              }
            }
          )
        )

    // Transitive super/interface closure of `binaryName`, excluding itself and platform classes.
    def transitiveAncestors(binaryName: String): Set[String] =
      transitiveAncestorsCache.getOrElseUpdate(
        binaryName, {
          val acc = mutable.Set.empty[String]
          val queue = mutable.Queue(binaryName)
          while (queue.nonEmpty) {
            val directParents = resolveClassFile(queue.dequeue()) match {
              case Some(cf) =>
                (cf.superClassName +: cf.interfaceNames.toIndexedSeq).filter(_.nonEmpty)
              case None => IndexedSeq.empty[String]
            }
            for (parent <- directParents if !isPlatformClass(parent) && acc.add(parent))
              queue.enqueue(parent)
          }
          acc.toSet
        }
      )

    // get class to class dependencies and map back to source to class dependencies
    for ((source, classFiles) <- sourceToClassFiles) {
      analysis.startSource(source)
      val loadedClasses = classFiles.flatMap(c => binaryClassNameToLoadedClass.get(c.className))
      // Local classes are either local, anonymous or inner Java classes
      val (nonLocalClasses, localClassesOrStale) =
        loadedClasses.partition(_.getCanonicalName != null)

      // Map local classes to the sources of their enclosing classes
      val localClassesToSources = {
        val localToSourcesSeq = for {
          cls <- localClassesOrStale
          sourceOfEnclosing <- loadEnclosingClass(cls)
        } yield (cls.getName, sourceOfEnclosing)
        localToSourcesSeq.toMap
      }

      /* Get the mapped source file from a given class name. */
      def getMappedSource(className: String): Option[String] = {
        val nonLocalSourceName: Option[String] = for {
          loadedClass <- binaryClassNameToLoadedClass.get(className)
          sourceName <- binaryToSourceName(loadedClass)
        } yield sourceName
        nonLocalSourceName
          .orElse(binaryClassNameToSourceName.get(className))
          .orElse(localClassesToSources.get(className))
      }

      def processDependency(
          onBinaryName: String,
          context: DependencyContext,
          fromBinaryName: String
      ): Unit = {
        def loadFromClassloader(): Option[Path] = {
          for {
            url <- Option(loader.getResource(classNameToClassFile(onBinaryName)))
            file <- urlAsFile(url, log, finalJarOutput)
          } yield { classfilesCache(onBinaryName) = file; file }
        }

        (getMappedSource(fromBinaryName), getMappedSource(onBinaryName)) match {
          case (Some(fromClassName), Some(onClassName)) =>
            trapAndLog(log) {
              analysis.classDependency(onClassName, fromClassName, context)
            }
          case (Some(fromClassName), None) =>
            trapAndLog(log) {
              val cachedOrigin = classfilesCache.get(onBinaryName)
              cachedOrigin.orElse(loadFromClassloader()) match {
                case Some(file) =>
                  val binaryFile: Path = remapClassFile(file)
                  analysis.binaryDependency(
                    binaryFile,
                    onBinaryName,
                    fromClassName,
                    source,
                    context
                  )
                case None =>
                  // sbt/zinc#149: the classfile is absent from the analysis classpath, so this
                  // dependency cannot be tracked. Guard platform classes first (every classfile
                  // references java.lang.*, and jrt-served classes are never tracked); a URL that
                  // existed but couldn't be converted was already warned about in urlAsFile.
                  if (!isPlatformClass(onBinaryName) && resourceUrl(onBinaryName).isEmpty)
                    missingClassReferrers.getOrElseUpdate(onBinaryName, mutable.Set.empty) +=
                      s"$fromClassName (${source.name})"
              }
            }
          case (None, _) => // It could be a stale class file, ignore
        }
      }
      def processDependencies(
          binaryClassNames: Iterable[String],
          context: DependencyContext,
          fromBinaryClassName: String
      ): Unit =
        binaryClassNames.foreach(binaryClassName =>
          processDependency(binaryClassName, context, fromBinaryClassName)
        )

      // Get all references to types in a given class file (via constant pool)
      val typesInSource = classFiles.map(cf => cf.className -> cf.types).toMap

      // Process dependencies by member references
      typesInSource foreach {
        case (binaryClassName, binaryClassNameDeps) =>
          processDependencies(binaryClassNameDeps, DependencyByMemberRef, binaryClassName)
          // sbt/zinc#148: also depend on the transitive ancestry of each referenced type (see above).
          val ancestors = binaryClassNameDeps.iterator
            .filterNot(isPlatformClass)
            .flatMap(transitiveAncestors)
            .toSet
          processDependencies(
            ancestors -- binaryClassNameDeps,
            DependencyByMemberRef,
            binaryClassName
          )
      }

      // sbt/zinc#145: javac inlines `static final` constants, erasing the reference to the declaring
      // class from this class's bytecode. The AST-derived `constantDeps` restore those member-ref
      // edges. Process them here (inside the per-source loop) so the classpath-origin branch of
      // `processDependency` resolves against the right `source`.
      for {
        binaryClassName <- typesInSource.keysIterator
        onBinaryName <- constantDeps.getOrElse(binaryClassName, Set.empty)
      } processDependency(onBinaryName, DependencyByMemberRef, binaryClassName)

      // sbt/zinc#149: reflecting over a class's signatures throws when a referenced class is
      // missing from the classpath (javac itself succeeded). Never fail the compile for that:
      // retry class-by-class to isolate failures; failing non-local classes are demoted to the
      // classfile-based fallback (sbt/zinc#837 loops below), failing local classes are skipped.
      // Structural errors (VerifyError, ClassFormatError) still propagate, matching
      // ClassToAPI.loadInnerClass.
      def readInheritanceDependencies(classes: Seq[Class[?]]): Map[String, Set[String]] = {
        def group(pairs: collection.Set[(String, String)]): Map[String, Set[String]] =
          // avoid .mapValues(...) because of its viewness (scala/bug#10919)
          pairs.groupBy(_._1).map { case (k, v) => k -> v.iterator.map(_._2).toSet }
        try group(readAPI(source, classes))
        catch {
          case e: (ClassNotFoundException | NoClassDefFoundError | TypeNotPresentException |
                IllegalAccessError) =>
            log.debug(
              s"[zinc] sbt/zinc#149: API extraction failed for ${source.name} ($e); " +
                "retrying per class"
            )
            val pairs = mutable.Set.empty[(String, String)]
            for (cls <- classes)
              try pairs ++= readAPI(source, Seq(cls))
              catch {
                case e: (ClassNotFoundException | NoClassDefFoundError | TypeNotPresentException |
                      IllegalAccessError) =>
                  Option(cls.getCanonicalName) match {
                    case Some(canonical) =>
                      log.warn(
                        s"While analyzing $canonical (${source.name}), failed to extract its API " +
                          s"by reflection: $e. Falling back to classfile-based analysis for this " +
                          "class."
                      )
                      binaryClassNameToSourceName.update(cls.getName, canonical)
                      recordMissingClass(e, s"$canonical (${source.name})")
                    case None =>
                      log.warn(
                        s"While analyzing ${cls.getName} (${source.name}), failed to extract its " +
                          s"API by reflection: $e. Inheritance dependencies of this local class " +
                          "will not be tracked."
                      )
                      recordMissingClass(e, s"${cls.getName} (${source.name})")
                  }
              }
            group(pairs)
        }
      }

      // Read API of non-local classes and process dependencies by inheritance
      val nonLocalInherited: Map[String, Set[String]] =
        readInheritanceDependencies(nonLocalClasses.toSeq)
      nonLocalInherited foreach {
        case (className, inheritanceDeps) =>
          processDependencies(inheritanceDeps, DependencyByInheritance, className)
      }

      // Read API of local classes and process local dependencies by inheritance
      val localClasses =
        localClassesOrStale.filter(cls => localClassesToSources.contains(cls.getName))
      val localInherited: Map[String, Set[String]] =
        readInheritanceDependencies(localClasses.toSeq)
      localInherited foreach {
        case (className, inheritanceDeps) =>
          processDependencies(inheritanceDeps, LocalDependencyByInheritance, className)
      }

      // sbt/zinc#837: classes that couldn't be reflectively loaded have no extractable API, but
      // their direct superclass and interfaces are still in the classfile, so record those
      // inheritance edges (e.g. the `Inner extends pkg.Base` relationship that caused the issue).
      for (classFile <- classFiles if binaryClassNameToSourceName.contains(classFile.className)) {
        val parents =
          (classFile.superClassName +: classFile.interfaceNames.toIndexedSeq).filter(_.nonEmpty)
        processDependencies(parents, DependencyByInheritance, classFile.className)
      }

      // sbt/zinc#837 (Phase 2): record API for un-loadable classes from the classfile, so
      // name-hashing detects changes to their own public shape (reflection can't load them).
      val unloadableNamed = classFiles.iterator
        .filter(cf => binaryClassNameToSourceName.contains(cf.className))
        .map(cf => binaryClassNameToSourceName(cf.className) -> cf)
        .toSeq
      // Best-effort: never let classfile-based API extraction fail a compile that would otherwise
      // succeed (these classes already couldn't be loaded), matching load()/loadInnerClass.
      if (unloadableNamed.nonEmpty) trapAndLog(log)(readClassfileAPI(source, unloadableNamed))
    }

    // sbt/zinc#149: one deterministic warning per missing class, naming the classes that reached
    // it (capped), in the spirit of scalac's "symbol is missing from the classpath" stub errors.
    for ((missing, referrers) <- missingClassReferrers.toSeq.sortBy(_._1)) {
      val sorted = referrers.toSeq.sorted
      val shown =
        if (sorted.sizeIs <= 5) sorted.mkString(", ")
        else sorted.take(5).mkString(", ") + s", and ${sorted.size - 5} more"
      log.warn(
        s"While analyzing $shown, failed to locate $missing. This class must be present on " +
          "the classpath in order to track dependencies on it."
      )
    }
  }

  /**
   * When straight-to-jar compilation is enabled on a javac which doesn't support it, classes are compiled to a
   * temporary directory because javac cannot compile to jar directly. The paths to class files that can be observed
   * here through the file system or class loaders are located in temporary output directory for
   * javac. As this output will be eventually included in the output jar (`finalJarOutput`), the
   * analysis (products) have to be changed accordingly.
   *
   * Given `finalJarOutput = Some("/develop/zinc/target/output.jar")` and
   * `output = "/develop/zinc/target/output.jar-javac-output"`, this method turns
   *   `/develop/zinc/target/output.jar-javac-output/sbt/internal/inc/Compile.class`
   * into
   *   `/develop/zinc/target/output.jar!/sbt/internal/inc/Compile.class`
   */
  private def resolveFinalClassFile(
      realClassFile: Path,
      outputDir: Path,
      outputJar: Path,
      log: Logger
  ): Path = {
    def toFile(p: Path): File = if (p == null) null else p.toFile
    IO.relativize(toFile(outputDir), toFile(realClassFile)) match {
      case Some(relativeClass) => JarUtils.ClassInJar(outputJar, relativeClass).toPath
      case None                => realClassFile
    }
  }

  private def urlAsFile(url: URL, log: Logger, finalJarOutput: Option[Path]): Option[Path] =
    try urlAsFile(url, finalJarOutput)
    catch {
      case e: Exception =>
        log.warn("Could not convert URL '" + url.toExternalForm + "' to File: " + e.toString)
        None
    }

  private def urlAsFile(url: URL, finalJarOutput: Option[Path]): Option[Path] = {
    IO.urlAsFile(url).map { file =>
      val p = file.toPath
      // IO.urlAsFile removes the class reference in the jar url, let's add it back.
      if (finalJarOutput.exists(_ == p)) {
        JarUtils.ClassInJar.fromURL(url, p).toPath
      } else {
        p
      }
    }
  }

  private def trapAndLog(log: Logger)(execute: => Unit): Unit = {
    try {
      execute
    } catch { case e: Throwable => log.trace(e); log.error(e.toString) }
  }
  private def guessSourceName(name: String) = Some(takeToDollar(trimClassExt(name)))
  private def takeToDollar(name: String) = {
    val dollar = name.indexOf('$')
    if (dollar < 0) name else name.substring(0, dollar)
  }
  private final val ClassExt = ".class"
  private def trimClassExt(name: String) =
    if (name.endsWith(ClassExt)) name.substring(0, name.length - ClassExt.length) else name
  private def classNameToClassFile(name: String) = name.replace('.', '/') + ClassExt
  private def binaryToSourceName(loadedClass: Class[?]): Option[String] =
    Option(loadedClass.getCanonicalName)

  /**
   * Best-effort name of the class that a reflection failure reports as missing (sbt/zinc#149).
   * The JVM names it in internal (com/b/B) or source form depending on the exception; sentence
   * messages (e.g. IllegalAccessError's) yield None.
   */
  private def missingClassName(e: Throwable): Option[String] = e match {
    case t: TypeNotPresentException => Some(t.typeName)
    case _: ClassNotFoundException | _: NoClassDefFoundError =>
      Option(e.getMessage).filter(m => m.nonEmpty && !m.contains(' ')).map(_.replace('/', '.'))
    case _ => None
  }

  @tailrec
  private def loadEnclosingClass(clazz: Class[?]): Option[String] = {
    binaryToSourceName(clazz) match {
      case None if clazz.getEnclosingClass != null =>
        loadEnclosingClass(clazz.getEnclosingClass)
      case other => other
    }
  }

  /**
   * Reconstructs the canonical name of a class from its classfile's InnerClasses attribute, for
   * classes that cannot be reflectively loaded (sbt/zinc#837). This mirrors the canonical name that
   * [[loadEnclosingClass]] derives via reflection for member and top-level classes, using the
   * authoritative simple names from the attribute. Returns None for local and anonymous classes
   * (which have no canonical name and are recorded as local products instead).
   */
  private def canonicalClassName(classFile: ClassFile): Option[String] = {
    val inners = classFile.innerClasses
    def canonical(binaryName: String): Option[String] =
      inners.find(_.innerClassName == binaryName) match {
        case None                                      => Some(binaryName) // top-level class
        case Some(info) if info.outerClassName.isEmpty => None // local or anonymous class
        case Some(info) =>
          canonical(info.outerClassName).map(_ + "." + info.innerName.getOrElse(binaryName))
      }
    canonical(classFile.className)
  }

  /*
   * given mapping between getName and sources, try to guess
   * where the *.class file is coming from.
   */
  private def guessSourcePath(
      sourceNameMap: Map[String, Set[VirtualFile]],
      classFile: ClassFile,
      log: Logger
  ): List[VirtualFile] = {
    val classNameParts = classFile.className.split("""\.""")
    val pkg = classNameParts.init
    val simpleClassName = classNameParts.last
    val sourceFileName =
      classFile.sourceFile.getOrElse(simpleClassName.takeWhile(_ != '$').mkString("", "", ".java"))
    val candidates = findSource(sourceNameMap, pkg.toList, sourceFileName)
    candidates match {
      case Nil      => log.warn("Could not determine source for class " + classFile.className)
      case _ :: Nil => ()
      case _ =>
        log.warn(
          "Multiple sources matched for class " + classFile.className + ": " + candidates
            .mkString(", ")
        )
    }
    candidates
  }

  private def findSource(
      sourceNameMap: Map[String, Iterable[VirtualFile]],
      pkg: List[String],
      sourceFileName: String
  ): List[VirtualFile] = {

    @tailrec def refine(
        sources: List[(VirtualFile, List[String])],
        pkgRev: List[String]
    ): List[VirtualFile] = {
      def make = sources.map(_._1)
      if (sources.isEmpty || sources.tail.isEmpty) make
      else
        pkgRev match {
          case Nil => shortest(make)
          case x :: xs =>
            val retain = sources flatMap {
              case (src, `x` :: presRev) => (src, presRev) :: Nil
              case _                     => Nil
            }
            refine(retain, xs)
        }
    }
    def shortest(files: List[VirtualFile]): List[VirtualFile] =
      if (files.isEmpty) files
      else {
        val fs = files.groupBy(x => x.names.size)
        fs(fs.keys.min)
      }

    refine(
      (sourceNameMap get sourceFileName).toList.flatten map { x =>
        (x, x.names.toList.reverse.drop(1))
      },
      pkg.reverse
    )
  }

}
