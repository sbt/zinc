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
package inc

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{ Files, Path, Paths }
import java.util.Optional
import sbt.internal.inc._
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.util.ManagedLogger
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.{ InterfaceUtil, Logger }
import xsbti.{ FileConverter, VirtualFile }
import xsbti.compile.{ ScalaInstance => _, _ }
import xsbti.compile.FileAnalysisStore

case class TestProjectSetup(
    baseLocation: Path,
    sources: Map[Path, Seq[Path]],
    classPath: Seq[Path],
    analysisForCp: Map[VirtualFile, Path] = Map.empty,
    outputToJar: Boolean = false,
    subproject: String = "unnamed",
    scalacOptions: Seq[String] = Nil,
) {
  import TestProjectSetup._

  private def fromResource(prefix: Path)(path: Path): File = {
    val fullPath = prefix.resolve(path).toString()
    try {
      Option(getClass.getClassLoader.getResource(fullPath))
        .map(url => new File(url.toURI))
        .getOrElse(throw new NoSuchElementException(s"Missing resource $fullPath"))
    } catch {
      case _: Throwable => sys.error(s"path = '$path' ($fullPath) not found")
    }
  }

  private val localBoot = Paths.get(sys.props("user.home")).resolve(".sbt").resolve("boot")
  private val javaHome = Paths.get(sys.props("java.home"))
  private val sourcesPrefix = Paths.get("sources")
  private val binPrefix = Paths.get("bin")

  val allSources: Iterable[Path] = for {
    (destinationPath, sourceFiles) <- sources
    destinationRoot = baseLocation.resolve(destinationPath)
    sourceFile <- sourceFiles
  } yield {
    val targetFile = destinationRoot.resolve(sourceFile).toFile
    val sourcePath = baseLocation.resolve(sourceFile)
    if (sourceFile.toString == "") {
      sys.error("unexpected blank sourceFile")
    }
    IO.copyFile(
      if (sourcePath.isAbsolute && Files.exists(sourcePath)) sourcePath.toFile
      else fromResource(sourcesPrefix)(sourceFile),
      targetFile
    )

    targetFile.toPath
  }
  val classpathBase = baseLocation.resolve("bin")
  val rootPaths = Vector(baseLocation, localBoot, javaHome)
  val converter = new MappedFileConverter(rootPaths, true)
  val allClasspath: Seq[VirtualFile] = (classPath map {
    case zippedClassesPath if zippedClassesPath.toString.endsWith(".zip") =>
      val target = classpathBase.resolve(zippedClassesPath.toString.dropRight(4)).toFile
      IO.unzip(fromResource(binPrefix)(zippedClassesPath), target)
      target.toPath
    case existingFile if Files.exists(existingFile) =>
      existingFile
    case jarPath =>
      val newJar = classpathBase.resolve(jarPath).toFile
      IO.copyFile(fromResource(binPrefix)(jarPath), newJar)
      newJar.toPath
  }).map(converter.toVirtualFile(_))

  val defaultClassesDir: Path = baseLocation.resolve("classes")
  val output: Path =
    if (outputToJar) baseLocation.resolve("target").resolve("output.jar")
    else defaultClassesDir

  def defaultStoreLocation: Path = baseLocation.resolve("inc_data.zip")

  def createCompiler(
      scalaVersion: String,
      si: xsbti.compile.ScalaInstance,
      compilerBridge: Path,
      log: ManagedLogger
  ) =
    CompilerSetup(
      scalaVersion,
      si,
      compilerBridge,
      output,
      baseLocation,
      allSources.toVector map converter.toVirtualFile,
      allClasspath,
      scalacOptions,
      IncOptions.of(),
      analysisForCp,
      defaultStoreLocation,
      converter,
      log
    )

  def update(source: Path)(change: String => String): Unit = {
    import collection.JavaConverters._
    val sourceFile = baseLocation.resolve(source)
    val text = Files.readAllLines(sourceFile).asScala.mkString("\n")
    Files.write(sourceFile, Seq(change(text)).asJava)
    ()
  }

  def dependsOnJarFrom(other: TestProjectSetup): TestProjectSetup = {
    val sources = other.defaultClassesDir.toFile ** "*.class"
    val mapping = sources.get.map { file =>
      file -> other.defaultClassesDir.relativize(file.toPath).toString
    }
    val dest = baseLocation.resolve("bin").resolve(s"${other.baseLocation.getFileName}.jar")
    IO.zip(mapping, dest.toFile, Some(0L))
    val vdest = PlainVirtualFile(dest)
    copy(
      classPath = classPath :+ dest,
      analysisForCp = analysisForCp + (vdest -> other.defaultStoreLocation)
    )
  }
}

object TestProjectSetup {
  def simple(baseLocation: Path, classes: Seq[String]): TestProjectSetup =
    TestProjectSetup(
      baseLocation,
      Map(Paths.get("src") -> classes.map(path => Paths.get(path))),
      Nil,
      Map.empty
    )

  def scalaCompiler(instance: xsbti.compile.ScalaInstance, bridgeJar: Path): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(instance, bridgeJar.toFile)
    val classpath = ClasspathOptionsUtil.boot
    val cache = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(instance, bridgeProvider, classpath, _ => (), cache)
  }

  case class CompilerSetup(
      scalaVersion: String,
      si: xsbti.compile.ScalaInstance,
      compilerBridge: Path,
      output: Path,
      tempDir: Path,
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      scalacOptions: Seq[String],
      incOptions: IncOptions,
      analysisForCp: Map[VirtualFile, Path],
      analysisStoreLocation: Path,
      converter: FileConverter,
      log: ManagedLogger
  ) {
    val maxErrors = 100
    val noLogger = Logger.Null
    val compiler = new IncrementalCompilerImpl
    val sc = scalaCompiler(si, compilerBridge)
    val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)

    private def analysis(forEntry: VirtualFile): Optional[CompileAnalysis] = {
      analysisForCp.get(forEntry) match {
        case Some(analysisStore) =>
          val content = FileAnalysisStore.getDefault(analysisStore.toFile).get()
          if (content.isPresent) Optional.of(content.get().getAnalysis)
          else Optional.empty()
        case _ =>
          Optional.empty()
      }
    }

    val lookup = MockedLookup(analysis)
    val mapper = VirtualFileUtil.sourcePositionMapper(converter)
    val reporter = new ManagedLoggedReporter(maxErrors, log, mapper)
    val extra = Array(InterfaceUtil.t2(("key", "value")))

    var lastCompiledUnits: Set[String] = Set.empty
    val progress = new CompileProgress {
      override def advance(current: Int, total: Int): Boolean = true

      override def startUnit(phase: String, unitPath: String): Unit = lastCompiledUnits += unitPath
    }

    val setup = compiler.setup(
      lookup,
      skip = false,
      tempDir.resolve("inc_compile"),
      CompilerCache.fresh,
      incOptions,
      reporter,
      Some(progress),
      extra
    )
    val prev = compiler.emptyPreviousResult
    val cp = Vector(converter.toVirtualFile(output)) ++
      (si.allJars map { x =>
        converter.toVirtualFile(x.toPath)
      }) ++
      classpath.toVector
    val stamper = Stamps.timeWrapLibraryStamps(converter)
    val in = compiler.inputs(
      cp.toArray,
      sources.toArray,
      output,
      scalacOptions.toArray,
      Array(),
      maxErrors,
      Array(),
      CompileOrder.Mixed,
      cs,
      setup,
      prev,
      Optional.empty(),
      converter,
      stamper
    )

    def doCompile(newInputs: Inputs => Inputs = identity): CompileResult = {
      lastCompiledUnits = Set.empty
      compiler.compile(newInputs(in), log)
    }

    def doCompileWithStore(
        store: AnalysisStore = FileAnalysisStore.getDefault(analysisStoreLocation.toFile),
        newInputs: Inputs => Inputs = identity
    ): CompileResult = {
      import JavaInterfaceUtil.EnrichOptional
      val previousResult = store.get().toOption match {
        case Some(analysisContents) =>
          val prevAnalysis = analysisContents.getAnalysis
          val prevSetup = analysisContents.getMiniSetup
          PreviousResult.of(
            Optional.of[CompileAnalysis](prevAnalysis),
            Optional.of[MiniSetup](prevSetup)
          )
        case _ =>
          compiler.emptyPreviousResult
      }
      val newResult = doCompile(in => newInputs(in.withPreviousResult(previousResult)))

      store.set(AnalysisContents.create(newResult.analysis(), newResult.setup()))
      newResult
    }
    def close(): Unit = {
      sc.classLoaderCache.foreach(_.close())
    }
  }

  case class MockedLookup(am: VirtualFile => Optional[CompileAnalysis])
      extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] =
      am(classpathEntry)

    override def definesClass(classpathEntry: VirtualFile): DefinesClass =
      Locate.definesClass(classpathEntry)
  }
}
