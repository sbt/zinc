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
      readAPI: (VirtualFileRef, Seq[Class[?]]) => Set[(String, String)]
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
      load(binaryClassName, Some("Error reading API from class file: " + binaryClassName)) match {
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
          classFileSourceName(classFile) match {
            case Some(className) =>
              analysis.generatedNonLocalClass(source, finalClassFile, binaryClassName, className)
              binaryClassNameToSourceName.update(binaryClassName, className)
            case None => analysis.generatedLocalClass(source, finalClassFile)
          }
      }

      sourceToClassFiles(source) += classFile
    }

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
              for (file <- cachedOrigin.orElse(loadFromClassloader())) {
                val binaryFile: Path = remapClassFile(file)
                analysis.binaryDependency(
                  binaryFile,
                  onBinaryName,
                  fromClassName,
                  source,
                  context
                )
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
      }

      def readInheritanceDependencies(classes: Seq[Class[?]]) = {
        val api = readAPI(source, classes)
        // avoid .mapValues(...) because of its viewness (scala/bug#10919)
        api.groupBy(_._1).iterator.map { case (k, v) => k -> v.map(_._2) }
      }

      // Read API of non-local classes and process dependencies by inheritance
      val nonLocalInherited: Map[String, Set[String]] =
        readInheritanceDependencies(nonLocalClasses.toSeq).toMap
      nonLocalInherited foreach {
        case (className, inheritanceDeps) =>
          processDependencies(inheritanceDeps, DependencyByInheritance, className)
      }

      // Read API of local classes and process local dependencies by inheritance
      val localClasses =
        localClassesOrStale.filter(cls => localClassesToSources.contains(cls.getName))
      val localInherited: Map[String, Set[String]] =
        readInheritanceDependencies(localClasses.toSeq).toMap
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

  @tailrec
  private def loadEnclosingClass(clazz: Class[?]): Option[String] = {
    binaryToSourceName(clazz) match {
      case None if clazz.getEnclosingClass != null =>
        loadEnclosingClass(clazz.getEnclosingClass)
      case other => other
    }
  }

  /**
   * Reconstructs the source (canonical) name of a class from its classfile's InnerClasses
   * attribute, for classes that cannot be reflectively loaded (sbt/zinc#837). This mirrors the
   * canonical name that [[loadEnclosingClass]] derives via reflection for member and top-level
   * classes, using the authoritative simple names from the attribute. Returns None for local and
   * anonymous classes (which have no canonical name and are recorded as local products instead).
   */
  private def classFileSourceName(classFile: ClassFile): Option[String] = {
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
