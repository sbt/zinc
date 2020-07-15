package sbt
package internal
package inc

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{ Files, Path, Paths }
import java.util.Optional

import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.io.IO
import sbt.io.syntax._
import xsbti.compile._
import sbt.util.Logger
import xsbti.TestCallback.ExtractedClassDependencies
import xsbti.{ ReporterUtil, TestCallback, UseScope, VirtualFile, VirtualFileRef }
import xsbti.api.ClassLike
import xsbti.api.DependencyContext._

/**
 * Provides common functionality needed for unit tests that require compiling
 * source code using Scala compiler.
 */
trait CompilingSpecification extends BridgeProviderSpecification {
  def scalaVersion =
    sys.props
      .get("zinc.build.compilerbridge.scalaVersion")
      .getOrElse(sys.error("zinc.build.compilerbridge.scalaVersion property not found"))
  def maxErrors = 100

  def scalaCompiler(instance: xsbti.compile.ScalaInstance, bridgeJar: Path): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(instance, bridgeJar)
    val classpath = ClasspathOptionsUtil.boot
    val cache = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(instance, bridgeProvider, classpath, _ => (), cache)
  }

  /**
   * Compiles given source code using Scala compiler and returns API representation
   * extracted by ExtractAPI class.
   */
  def extractApisFromSrc(src: String): Set[ClassLike] = {
    val (Seq(tempSrcFile), analysisCallback) = compileSrcs(src)
    analysisCallback.apis(tempSrcFile)
  }

  /**
   * Compiles given source code using Scala compiler and returns API representation
   * extracted by ExtractAPI class.
   */
  def extractApisFromSrcs(srcs: List[String]*): Seq[Set[ClassLike]] = {
    val (tempSrcFiles, analysisCallback) = compileSrcs(srcs.toList)
    tempSrcFiles.map(analysisCallback.apis)
  }

  def extractUsedNamesFromSrc(src: String): Map[String, Set[String]] = {
    val (_, analysisCallback) = compileSrcs(src)
    analysisCallback.usedNames.toMap
  }

  def extractBinaryDependenciesFromSrcs(srcs: List[List[String]]): ExtractedClassDependencies = {
    val (_, testCallback) = compileSrcs(srcs)
    val binaryDependencies = testCallback.binaryDependencies
    ExtractedClassDependencies.fromPairs(
      binaryDependencies.collect { case (_, bin, src, DependencyByMemberRef)        => src -> bin },
      binaryDependencies.collect { case (_, bin, src, DependencyByInheritance)      => src -> bin },
      binaryDependencies.collect { case (_, bin, src, LocalDependencyByInheritance) => src -> bin },
    )
  }

  def extractBinaryClassNamesFromSrc(src: String): Set[(String, String)] = {
    val (Seq(tempSrcFile), analysisCallback) = compileSrcs(src)
    analysisCallback.classNames(tempSrcFile).toSet
  }

  /**
   * Extract used names from src provided as the second argument.
   * If `assertDefaultScope` is set to true it will fail if there is any name used in scope other then Default
   *
   * The purpose of the first argument is to define names that the second
   * source is going to refer to. Both files are compiled in the same compiler
   * Run but only names used in the second src file are returned.
   */
  def extractUsedNamesFromSrc(
      definitionSrc: String,
      actualSrc: String,
      assertDefaultScope: Boolean = true
  ): Map[String, Set[String]] = {
    // we drop temp src file corresponding to the definition src file
    val (Seq(_, tempSrcFile), analysisCallback) = compileSrcs(definitionSrc, actualSrc)

    if (assertDefaultScope) for {
      (className, used) <- analysisCallback.usedNamesAndScopes
      analysisCallback.TestUsedName(name, scopes) <- used
    } assert(scopes.size() == 1 && scopes.contains(UseScope.Default), s"$className uses $name in $scopes")

    val classesInActualSrc = analysisCallback.classNames(tempSrcFile).map(_._1)
    classesInActualSrc.map(className => className -> analysisCallback.usedNames(className)).toMap
  }

  /**
   * Extract used names from the last source file in `sources`.
   *
   * The previous source files are provided to successfully compile examples.
   * Only the names used in the last src file are returned.
   */
  def extractUsedNamesFromSrc(sources: String*): Map[String, Set[String]] = {
    val (srcFiles, analysisCallback) = compileSrcs(sources: _*)
    srcFiles
      .map { srcFile =>
        val classesInSrc = analysisCallback.classNames(srcFile).map(_._1)
        classesInSrc.map(className => className -> analysisCallback.usedNames(className)).toMap
      }
      .reduce(_ ++ _)
  }

  /**
   * Compile the given source code snippets (passed as strings) and return the extracted
   * dependencies between snippets.
   */
  def extractDependenciesFromSrcs(srcs: String*): ExtractedClassDependencies = {
    val (_, testCallback) = compileSrcs(srcs: _*)

    val memberRefDeps = testCallback.classDependencies collect {
      case (target, src, DependencyByMemberRef) => (src, target)
    }
    val inheritanceDeps = testCallback.classDependencies collect {
      case (target, src, DependencyByInheritance) => (src, target)
    }
    val localInheritanceDeps = testCallback.classDependencies collect {
      case (target, src, LocalDependencyByInheritance) => (src, target)
    }
    ExtractedClassDependencies.fromPairs(memberRefDeps, inheritanceDeps, localInheritanceDeps)
  }

  val localBoot = Paths.get(sys.props("user.home")).resolve(".sbt").resolve("boot")
  val javaHome = Paths.get(sys.props("java.home"))
  val localCoursierCache: Map[String, Path] = Map(
    List(
      "C_CACHE1" -> Paths.get(sys.props("user.home")).resolve(".coursier").resolve("cache"),
      "C_CACHE2" -> Paths.get(sys.props("user.home")).resolve(".cache").resolve("coursier"),
      "C_CACHE3" -> Paths.get(sys.props("user.home"), "Library/Caches/Coursier/v1")
    ) ++ sys.env
      .get("LOCALAPPDATA")
      .map(s => "C_CACHE4" -> Paths.get(s.replace('\\', '/'), "Coursier/cache/v1"))
      .toList: _*
  )

  /**
   * Compiles given source code snippets written to temporary files. Each snippet is
   * written to a separate temporary file.
   *
   * Snippets can be grouped to be compiled together in the same compiler run. This is
   * useful to compile macros, which cannot be used in the same compilation run that
   * defines them.
   *
   * The sequence of temporary files corresponding to passed snippets and analysis
   * callback is returned as a result.
   */
  def compileSrcs(groupedSrcs: List[List[String]]): (Seq[VirtualFile], TestCallback) = {
    IO.withTemporaryDirectory { tempDir =>
      val rootPaths: Map[String, Path] = Map(
        "BASE" -> tempDir.toPath,
        "SBT_BOOT" -> localBoot,
        "JAVA_HOME" -> javaHome
      ) ++ localCoursierCache
      val converter = new MappedFileConverter(rootPaths, false)
      val targetDir = tempDir / "target"
      val analysisCallback = new TestCallback
      targetDir.mkdir()
      val files = for {
        (compilationUnit, unitId) <- groupedSrcs.zipWithIndex
      } yield {
        val srcFiles = compilationUnit.zipWithIndex map {
          case (src, i) =>
            val fileName = s"Test-$unitId-$i.scala"
            val f = prepareSrcFile(tempDir, fileName, src)
            converter.toVirtualFile(f.toPath)
        }
        val sources = srcFiles.toArray
        val noLogger = Logger.Null
        val compilerBridge = getCompilerBridge(tempDir.toPath, noLogger, scalaVersion)
        val si = scalaInstance(scalaVersion, tempDir.toPath, noLogger)
        val sc = scalaCompiler(si, compilerBridge)
        val cp = (si.allJars ++ Array(targetDir)).map(_.toPath).map(converter.toVirtualFile)
        val emptyChanges: DependencyChanges = new DependencyChanges {
          override val modifiedLibraries = new Array[VirtualFileRef](0)
          override val modifiedBinaries = new Array[File](0)
          override val modifiedClasses = new Array[String](0)
          override def isEmpty = true
        }
        val compArgs = new CompilerArguments(si, sc.classpathOptions)
        val arguments = compArgs.makeArguments(Nil, cp, Nil)
        val basicReporterConfig = ReporterUtil.getDefaultReporterConfig()
        val reporterConfig = basicReporterConfig.withMaximumErrors(maxErrors)
        val reporter = ReporterManager.getReporter(log, reporterConfig)
        sc.compile(
          sources = sources,
          changes = emptyChanges,
          options = arguments.toArray,
          output = CompileOutput(targetDir.toPath),
          callback = analysisCallback,
          reporter = reporter,
          progressOpt = Optional.empty[CompileProgress],
          log = log
        )
        srcFiles
      }

      // Make sure that the analysis doesn't lie about the class files that are written
      analysisCallback.productClassesToSources.keySet.foreach { classFile =>
        if (Files.exists(classFile)) ()
        else {
          val cfs = Files.list(classFile.getParent).toArray.mkString("\n")
          sys.error(s"Class file '${classFile}' doesn't exist! Found:\n$cfs")
        }
      }

      (files.flatten map { converter.toVirtualFile(_) }, analysisCallback)
    }
  }

  def compileSrcs(srcs: String*): (Seq[VirtualFile], TestCallback) = {
    compileSrcs(List(srcs.toList))
  }

  private def prepareSrcFile(baseDir: File, fileName: String, src: String): File = {
    val srcFile = new File(baseDir, fileName)
    IO.write(srcFile, src)
    srcFile
  }
}
