/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{ Files, Path, Paths }
import java.util.Optional

import sbt.internal.inc._
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.{ InterfaceUtil, Logger }
import xsbti.compile.{ ScalaInstance => _, _ }

class BaseCompilerSpec extends BridgeProviderSpecification {

  val scalaVersion = scala.util.Properties.versionNumberString
  val maxErrors = 100

  case class MockedLookup(am: File => Optional[CompileAnalysis]) extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Optional[CompileAnalysis] =
      am(classpathEntry)

    override def definesClass(classpathEntry: File): DefinesClass =
      Locate.definesClass(classpathEntry)
  }

  case class ProjectSetup(baseLocation: Path, sources: Map[Path, Seq[Path]], classPath: Seq[Path]) {
    private def fromResource(prefix: Path)(path: Path): File = {
      val fullPath = prefix.resolve(path).toString()
      Option(getClass.getClassLoader.getResource(fullPath))
        .map(url => new File(url.toURI))
        .getOrElse(throw new NoSuchElementException(s"Missing resource $fullPath"))
    }

    private val sourcesPrefix = Paths.get("sources")
    private val binPrefix = Paths.get("bin")

    val allSources: Iterable[File] = for {
      (sourcePath, sourceFiles) <- sources
      sourceRoot = baseLocation.resolve(sourcePath)
      sourceFile <- sourceFiles
    } yield {
      val targetFile = sourceRoot.resolve(sourceFile).toFile
      IO.copyFile(fromResource(sourcesPrefix)(sourceFile), targetFile)
      targetFile
    }
    val classpathBase = baseLocation.resolve("bin")

    val allClasspath = classPath.map {
      case zippedClassesPath if zippedClassesPath.getFileName.toString.endsWith(".zip") =>
        val target = classpathBase.resolve(zippedClassesPath.toString.dropRight(4)).toFile
        IO.unzip(fromResource(binPrefix)(zippedClassesPath), target)
        target
      case jarPath =>
        val newJar = classpathBase.resolve(jarPath).toFile
        IO.copyFile(fromResource(binPrefix)(jarPath), newJar)
        newJar
    }

    val defaultClassesDir = baseLocation.resolve("classes").toFile

    def defaultStoreLocation: File = baseLocation.resolve("inc_data.zip").toFile

    def createCompiler() =
      CompilerSetup(defaultClassesDir,
        baseLocation.toFile,
        allSources.toArray,
        allClasspath,
        IncOptions.of(),
        Seq("-Yrangepos"),
        Seq())

    def update(source: Path)(change: String => String): Unit = {
      import collection.JavaConverters._
      val sourceFile = baseLocation.resolve(source)
      val text = Files.readAllLines(sourceFile).asScala.mkString("\n")
      Files.write(sourceFile, Seq(change(text)).asJava)
    }
  }

  object ProjectSetup {
    def simple(baseLocation: Path, classes: Seq[String]): ProjectSetup =
      ProjectSetup(baseLocation, Map(Paths.get("src") -> classes.map(path => Paths.get(path))), Nil)
  }

  def scalaCompiler(instance: xsbti.compile.ScalaInstance, bridgeJar: File): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(instance, bridgeJar)
    val classpath = ClasspathOptionsUtil.boot
    val cache = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(instance, bridgeProvider, classpath, _ => (), cache)
  }

  case class CompilerSetup(
      classesDir: File,
      tempDir: File,
      sources: Array[File],
      classpath: Seq[File],
      incOptions: IncOptions = IncOptions.of(),
      scalacOptions: Seq[String] = Seq(),
      javacOptions: Seq[String] = Seq()
  ) {
    val noLogger = Logger.Null
    val compiler = new IncrementalCompilerImpl
    val compilerBridge = getCompilerBridge(tempDir, noLogger, scalaVersion)

    val si = scalaInstance(scalaVersion, tempDir, noLogger)
    val sc = scalaCompiler(si, compilerBridge)
    val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)

    val lookup = MockedLookup(Function.const(Optional.empty[CompileAnalysis]))
    val reporter = new ManagedLoggedReporter(maxErrors, log)
    val extra = Array(InterfaceUtil.t2(("key", "value")))

    var lastCompiledUnits: Set[String] = Set.empty
    val progress = new CompileProgress {
      override def advance(current: Int, total: Int): Boolean = true

      override def startUnit(phase: String, unitPath: String): Unit = lastCompiledUnits += unitPath
    }

    val setup = compiler.setup(lookup,
                               skip = false,
                               tempDir / "inc_compile",
                               CompilerCache.fresh,
                               incOptions,
                               reporter,
                               Some(progress),
                               extra)
    val prev = compiler.emptyPreviousResult
    val in = compiler.inputs(Array(classesDir) ++ si.allJars ++ classpath,
                             sources,
                             classesDir,
                             Array(),
                             Array(),
                             maxErrors,
                             Array(),
                             CompileOrder.Mixed,
                             cs,
                             setup,
                             prev)

    def doCompile(newInputs: Inputs => Inputs = identity): CompileResult = {
      lastCompiledUnits = Set.empty
      compiler.compile(newInputs(in), log)
    }

    def doCompileWithStore(store: AnalysisStore,
                           newInputs: Inputs => Inputs = identity): CompileResult = {
      import JavaInterfaceUtil.EnrichOptional
      val previousResult = store.get().toOption match {
        case Some(analysisContents) =>
          val prevAnalysis = analysisContents.getAnalysis
          val prevSetup = analysisContents.getMiniSetup
          PreviousResult.of(Optional.of[CompileAnalysis](prevAnalysis),
                            Optional.of[MiniSetup](prevSetup))
        case _ =>
          compiler.emptyPreviousResult
      }
      val newResult = doCompile(in => newInputs(in.withPreviousResult(previousResult)))

      store.set(AnalysisContents.create(newResult.analysis(), newResult.setup()))
      newResult
    }
  }

}
