/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
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
      readAPI: (VirtualFileRef, Seq[Class[_]]) => Set[(String, String)]
  ): Unit = {
    val sourceMap = sources
      .toSet[VirtualFile]
      .groupBy(_.name)
    // For performance reasons, precompute these as they are static throughout this analysis
    val outputJarOrNull: Path = finalJarOutput.getOrElse(null)
    val singleOutputOrNull: Path = output.getSingleOutput.orElse(null)

    def load(tpe: String, errMsg: => Option[String]): Option[Class[_]] = {
      if (tpe.endsWith("module-info")) None
      else
        try {
          Some(Class.forName(tpe, false, loader))
        } catch {
          case e: Throwable => errMsg.foreach(msg => log.warn(msg + " : " + e.toString)); None
        }
    }

    val classNames = mutable.Set.empty[String]
    val sourceToClassFiles = mutable.HashMap[VirtualFile, Buffer[ClassFile]](
      sources.map(vf => vf -> new ArrayBuffer[ClassFile]): _*
    )

    val binaryClassNameToLoadedClass = new mutable.HashMap[String, Class[_]]

    val classfilesCache = mutable.Map.empty[String, Path]

    // parse class files and assign classes to sources.  This must be done before dependencies, since the information comes
    // as class->class dependencies that must be mapped back to source->class dependencies using the source+class assignment
    for {
      newClass <- newClasses
      classFile = Parser(newClass)
      _ <- classFile.sourceFile orElse guessSourceName(newClass.getFileName.toString)
      source <- guessSourcePath(sourceMap, classFile, log)
      binaryClassName = classFile.className
      loadedClass <- load(
        binaryClassName,
        Some("Error reading API from class file: " + binaryClassName)
      )
    } {
      binaryClassNameToLoadedClass.update(binaryClassName, loadedClass)

      def loadEnclosingClass(clazz: Class[_]): Option[String] = {
        binaryToSourceName(clazz) match {
          case None if clazz.getEnclosingClass != null =>
            loadEnclosingClass(clazz.getEnclosingClass)
          case other => other
        }
      }

      val srcClassName = loadEnclosingClass(loadedClass)

      val finalClassFile: Path = {
        if (singleOutputOrNull == null || outputJarOrNull == null) newClass
        else
          resolveFinalClassFile(newClass, singleOutputOrNull, outputJarOrNull, log)
      }
      srcClassName match {
        case Some(className) =>
          analysis.generatedNonLocalClass(source, finalClassFile, binaryClassName, className)
          classNames += className
        case None => analysis.generatedLocalClass(source, finalClassFile)
      }

      sourceToClassFiles(source) += classFile
    }

    // get class to class dependencies and map back to source to class dependencies
    for ((source, classFiles) <- sourceToClassFiles) {
      analysis.startSource(source)
      val loadedClasses = classFiles.map(c => binaryClassNameToLoadedClass(c.className))
      // Local classes are either local, anonymous or inner Java classes
      val (nonLocalClasses, localClassesOrStale) =
        loadedClasses.partition(_.getCanonicalName != null)

      // Map local classes to the sources of their enclosing classes
      val localClassesToSources = {
        val localToSourcesSeq = for {
          cls <- localClassesOrStale
          enclosingCls <- Option(cls.getEnclosingClass)
          sourceOfEnclosing <- binaryToSourceName(enclosingCls)
        } yield (cls.getName, sourceOfEnclosing)
        localToSourcesSeq.toMap
      }

      /* Get the mapped source file from a given class name. */
      def getMappedSource(className: String): Option[String] = {
        val nonLocalSourceName: Option[String] = for {
          loadedClass <- binaryClassNameToLoadedClass.get(className)
          sourceName <- binaryToSourceName(loadedClass)
        } yield sourceName
        nonLocalSourceName.orElse(localClassesToSources.get(className))
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

        getMappedSource(fromBinaryName) match {
          case Some(fromClassName) =>
            trapAndLog(log) {
              val scalaLikeTypeName = onBinaryName.replace('$', '.')
              if (classNames.contains(scalaLikeTypeName)) {
                analysis.classDependency(scalaLikeTypeName, fromClassName, context)
              } else {
                val cachedOrigin = classfilesCache.get(onBinaryName)
                for (file <- cachedOrigin.orElse(loadFromClassloader())) {
                  val binaryFile: Path = {
                    if (singleOutputOrNull == null || outputJarOrNull == null) file
                    else
                      resolveFinalClassFile(file, singleOutputOrNull, outputJarOrNull, log)
                  }
                  analysis.binaryDependency(
                    binaryFile,
                    onBinaryName,
                    fromClassName,
                    source,
                    context
                  )
                }
              }
            }
          case None => // It could be a stale class file, ignore
        }
      }
      def processDependencies(
          binaryClassNames: Iterable[String],
          context: DependencyContext,
          fromBinaryClassName: String
      ): Unit =
        binaryClassNames.foreach(
          binaryClassName => processDependency(binaryClassName, context, fromBinaryClassName)
        )

      // Get all references to types in a given class file (via constant pool)
      val typesInSource = classFiles.map(cf => cf.className -> cf.types).toMap

      // Process dependencies by member references
      typesInSource foreach {
        case (binaryClassName, binaryClassNameDeps) =>
          processDependencies(binaryClassNameDeps, DependencyByMemberRef, binaryClassName)
      }

      def readInheritanceDependencies(classes: Seq[Class[_]]) = {
        val api = readAPI(source, classes)
        api.groupBy(_._1).mapValues(_.map(_._2))
      }

      // Read API of non-local classes and process dependencies by inheritance
      val nonLocalInherited: Map[String, Set[String]] =
        readInheritanceDependencies(nonLocalClasses)
      nonLocalInherited foreach {
        case (className, inheritanceDeps) =>
          processDependencies(inheritanceDeps, DependencyByInheritance, className)
      }

      // Read API of local classes and process local dependencies by inheritance
      val localClasses =
        localClassesOrStale.filter(cls => localClassesToSources.contains(cls.getName))
      val localInherited: Map[String, Set[String]] =
        readInheritanceDependencies(localClasses)
      localInherited foreach {
        case (className, inheritanceDeps) =>
          processDependencies(inheritanceDeps, LocalDependencyByInheritance, className)
      }
    }
  }

  /**
   * When straight-to-jar compilation is enabled, classes are compiled to a temporary directory
   * because javac cannot compile to jar directly. The paths to class files that can be observed
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

  private[this] def urlAsFile(url: URL, log: Logger, finalJarOutput: Option[Path]): Option[Path] =
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
  private def binaryToSourceName(loadedClass: Class[_]): Option[String] =
    Option(loadedClass.getCanonicalName)

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

    refine((sourceNameMap get sourceFileName).toList.flatten map { x =>
      (x, x.names.toList.reverse.drop(1))
    }, pkg.reverse)
  }

}
