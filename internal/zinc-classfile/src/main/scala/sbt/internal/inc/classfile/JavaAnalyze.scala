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
  def apply(
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
    val classFileMapper = new JavaClassFileMapper(output, finalJarOutput)
    val cachedLoader = new CachedClassLoader(loader, finalJarOutput, log)
    val analyzer = new JavaAnalyzer(sources, log, classFileMapper, analysis, cachedLoader, readAPI)
    analyzer.analyze(newClasses)
  }
}

private class JavaAnalyzer(
    sources: Seq[VirtualFile],
    log: Logger,
    classFileMapper: JavaClassFileMapper,
    analysis: xsbti.AnalysisCallback,
    loader: CachedClassLoader,
    readAPI: (VirtualFileRef, Seq[Class[?]]) => Set[(String, String)]
) {
  val sourceMap = sources.toSet[VirtualFile].groupBy(_.name)

  def analyze(newClasses: Seq[Path]): Unit = {
    // parse class files and assign classes to sources.  This must be done before dependencies, since the information comes
    // as class->class dependencies that must be mapped back to source->class dependencies using the source+class assignment
    val sourceToClassFiles = mapSourcesToClassFiles(newClasses)
    val binaryNamesToClasses = {
      for {
        classFile <- sourceToClassFiles.values.flatten
        className = classFile.className
        cls <- loader.load(className)
      } yield className -> cls
    }.toMap
    // get class to class dependencies and map back to source to class dependencies
    for ((source, classFiles) <- sourceToClassFiles) {
      analyzeSource(source, classFiles, binaryNamesToClasses)
    }
  }

  private def mapSourcesToClassFiles(newClasses: Seq[Path]): Map[VirtualFile, Seq[ClassFile]] = {
    val sourceToClassFiles = mutable.HashMap[VirtualFile, Buffer[ClassFile]](
      sources.map(vf => vf -> new ArrayBuffer[ClassFile]): _*
    )
    for {
      newClass <- newClasses
      classFile = Parser(newClass, log)
      _ <- classFile.sourceFile orElse guessSourceName(newClass.getFileName.toString)
      source <- guessSourcePath(classFile)
      binaryClassName = classFile.className
      loadedClass <- loader.load(binaryClassName)
    } {
      val srcClassName = loader.loadEnclosingClass(loadedClass)
      val finalClassFile: Path = classFileMapper.remap(newClass)
      srcClassName match {
        case Some(className) =>
          analysis.generatedNonLocalClass(source, finalClassFile, binaryClassName, className)
        case None => analysis.generatedLocalClass(source, finalClassFile)
      }
      sourceToClassFiles(source) += classFile
    }
    sourceToClassFiles.view.mapValues(_.toSeq).toMap
  }

  private def analyzeSource(
      source: VirtualFile,
      classFiles: Seq[ClassFile],
      binaryNamesToClasses: Map[String, Class[?]]
  ): Unit = {
    analysis.startSource(source)
    val loadedClasses = classFiles.flatMap(c => loader.load(c.className))
    // Local classes are either local, anonymous or inner Java classes
    val (nonLocalClasses, localClassesOrStale) =
      loadedClasses.partition(_.getCanonicalName != null)

    // Map local classes to the sources of their enclosing classes
    val localClassesToSources = {
      for {
        cls <- localClassesOrStale
        sourceOfEnclosing <- loader.loadEnclosingClass(cls)
      } yield (cls.getName, sourceOfEnclosing)
    }.toMap

    // Get all references to types in a given class file (via constant pool)
    val typesInSource = classFiles.map(cf => cf.className -> cf.types).toMap

    /* Get the mapped source file from a given class name. */
    def getMappedSource(className: String): Option[String] = {
      def nonLocalSourceName: Option[String] = for {
        loadedClass <- binaryNamesToClasses.get(className)
        sourceName <- loader.binaryToSourceName(loadedClass)
      } yield sourceName
      nonLocalSourceName.orElse(localClassesToSources.get(className))
    }

    def processDependency(
        onBinaryName: String,
        context: DependencyContext,
        fromBinaryName: String
    ): Unit = {
      (getMappedSource(fromBinaryName), getMappedSource(onBinaryName)) match {
        case (Some(fromClassName), Some(onClassName)) =>
          trapAndLog {
            analysis.classDependency(onClassName, fromClassName, context)
          }
        case (Some(fromClassName), None) =>
          trapAndLog {
            for (file <- loader.getClassFile(onBinaryName)) {
              val binaryFile: Path = classFileMapper.remap(file)
              analysis.binaryDependency(binaryFile, onBinaryName, fromClassName, source, context)
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

    // Process dependencies by member references
    typesInSource foreach {
      case (binaryClassName, binaryClassNameDeps) =>
        processDependencies(binaryClassNameDeps, DependencyByMemberRef, binaryClassName)
    }

    // avoid .mapValues(...) because of its viewness (scala/bug#10919)
    def readInheritanceDependencies(classes: Seq[Class[?]]): Map[String, Set[String]] =
      readAPI(source, classes).groupBy(_._1).iterator.map { case (k, v) => k -> v.map(_._2) }.toMap

    // Read API of non-local classes and process dependencies by inheritance
    readInheritanceDependencies(nonLocalClasses.toSeq).foreach {
      case (className, inheritanceDeps) =>
        processDependencies(inheritanceDeps, DependencyByInheritance, className)
    }

    // Read API of local classes and process local dependencies by inheritance
    val localClasses =
      localClassesOrStale.filter(cls => localClassesToSources.contains(cls.getName))
    readInheritanceDependencies(localClasses.toSeq).foreach {
      case (className, inheritanceDeps) =>
        processDependencies(inheritanceDeps, LocalDependencyByInheritance, className)
    }
  }

  private def trapAndLog(execute: => Unit): Unit = {
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

  /*
   * given mapping between getName and sources, try to guess
   * where the *.class file is coming from.
   */
  private def guessSourcePath(classFile: ClassFile): List[VirtualFile] = {
    val classNameParts = classFile.className.split("""\.""")
    val pkg = classNameParts.init
    val simpleClassName = classNameParts.last
    val sourceFileName =
      classFile.sourceFile.getOrElse(simpleClassName.takeWhile(_ != '$').mkString("", "", ".java"))
    val candidates = findSource(pkg.toList, sourceFileName)
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

    refine(
      (sourceMap get sourceFileName).toList.flatten map { x =>
        (x, x.names.toList.reverse.drop(1))
      },
      pkg.reverse
    )
  }

  private def shortest(files: List[VirtualFile]): List[VirtualFile] =
    if (files.isEmpty) files
    else {
      val fs = files.groupBy(x => x.names.size)
      fs(fs.keys.min)
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
private class JavaClassFileMapper(output: Output, finalJarOutput: Option[Path]) {
  // For performance reasons, precompute these as they are static throughout this analysis
  val singleOutputOrNull: Path = output.getSingleOutputAsPath.orElse(null)
  val directOutputJarOrNull: Path = JarUtils.getOutputJar(output).getOrElse(null)
  val mappedOutputJarOrNull: Path = finalJarOutput.getOrElse(null)

  def remap(classFile: Path): Path =
    if (directOutputJarOrNull != null && classFile.getFileSystem.provider.getScheme == "jar")
      // convert to the class-in-jar path format that zinc uses. we make an assumption here that
      // if we've got a jar-based path, it's referring to a class in the output jar.
      JarUtils
        .ClassInJar(directOutputJarOrNull, classFile.getRoot.relativize(classFile).toString)
        .toPath
    else if (singleOutputOrNull != null && mappedOutputJarOrNull != null)
      resolveFinalClassFile(classFile, singleOutputOrNull, mappedOutputJarOrNull)
    else
      classFile

  private def resolveFinalClassFile(
      realClassFile: Path,
      outputDir: Path,
      outputJar: Path
  ): Path = {
    def toFile(p: Path): File = if (p == null) null else p.toFile
    IO.relativize(toFile(outputDir), toFile(realClassFile)) match {
      case Some(relativeClass) => JarUtils.ClassInJar(outputJar, relativeClass).toPath
      case None                => realClassFile
    }
  }
}

private class CachedClassLoader(loader: ClassLoader, finalOutputJar: Option[Path], log: Logger) {
  private val classFilesCache = mutable.Map.empty[String, Option[Path]]
  private val loadedClassCache = mutable.Map.empty[String, Option[Class[?]]]

  def load(binaryClassName: String): Option[Class[?]] =
    loadedClassCache.getOrElseUpdate(
      binaryClassName,
      if (binaryClassName.endsWith("module-info")) None
      else
        try {
          Some(Class.forName(binaryClassName, false, loader))
        } catch {
          case e: Throwable =>
            log.warn(s"Error reading API from class file: $binaryClassName: $e")
            None
        }
    )

  @tailrec
  final def loadEnclosingClass(clazz: Class[?]): Option[String] = {
    binaryToSourceName(clazz) match {
      case None if clazz.getEnclosingClass != null =>
        loadEnclosingClass(clazz.getEnclosingClass)
      case other => other
    }
  }

  def binaryToSourceName(loadedClass: Class[?]): Option[String] =
    Option(loadedClass.getCanonicalName)

  def getClassFile(onBinaryName: String): Option[Path] =
    classFilesCache.getOrElseUpdate(
      onBinaryName,
      for {
        url <- Option(loader.getResource(classNameToClassFile(onBinaryName)))
        file <- urlAsFile(url)
      } yield file
    )

  private def classNameToClassFile(name: String) = name.replace('.', '/') + ".class"

  private def urlAsFile(url: URL): Option[Path] =
    try {
      IO.urlAsFile(url).map { file =>
        val p = file.toPath
        // IO.urlAsFile removes the class reference in the jar url, let's add it back.
        if (finalOutputJar.exists(_ == p)) {
          JarUtils.ClassInJar.fromURL(url, p).toPath
        } else {
          p
        }
      }
    } catch {
      case e: Exception =>
        log.warn("Could not convert URL '" + url.toExternalForm + "' to File: " + e.toString)
        None
    }
}
