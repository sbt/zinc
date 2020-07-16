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

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }
import java.util.Optional

import sbt.internal.inc._
import sbt.internal.inc.JavaInterfaceUtil.EnrichOptional
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.util.ManagedLogger
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.{ InterfaceUtil, Logger }

import xsbti.{ FileConverter, VirtualFile }
import xsbti.compile.{ ScalaInstance => XScalaInstance, _ }
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
    getClass.getClassLoader.getResource(fullPath) match {
      case null => sys.error(s"path = '$path' ($fullPath) not found")
      case url  => new File(url.toURI)
    }
  }

  private val localBoot = Paths.get(sys.props("user.home")).resolve(".sbt/boot")
  private val javaHome = Paths.get(sys.props("java.home"))
  private val sourcesPrefix = Paths.get("sources")
  private val binPrefix = Paths.get("bin")

  val allSources: Iterable[Path] = for {
    (destinationPath, sourceFiles) <- sources
    destinationRoot = baseLocation.resolve(destinationPath)
    sourceFile <- sourceFiles
  } yield {
    val targetFile = destinationRoot.resolve(sourceFile)
    val sourcePath = baseLocation.resolve(sourceFile)
    if (sourceFile.toString == "") {
      sys.error("unexpected blank sourceFile")
    }
    IO.copyFile(
      if (Files.exists(sourcePath)) sourcePath.toFile
      else fromResource(sourcesPrefix)(sourceFile),
      targetFile.toFile
    )

    targetFile
  }

  val classpathBase = baseLocation.resolve("bin")
  val rootPaths = Map("BASE" -> baseLocation, "SBT_BOOT" -> localBoot, "JAVA_HOME" -> javaHome)
  val converter = new MappedFileConverter(rootPaths, true)

  val allClasspath: Seq[Path] = classPath.map {
    case zippedClassesPath if zippedClassesPath.toString.endsWith(".zip") =>
      val target = classpathBase.resolve(zippedClassesPath.toString.stripSuffix(".zip"))
      IO.unzip(fromResource(binPrefix)(zippedClassesPath), target.toFile)
      target
    case existingFile if Files.exists(existingFile) =>
      existingFile
    case jarPath =>
      val newJar = classpathBase.resolve(jarPath)
      IO.copyFile(fromResource(binPrefix)(jarPath), newJar.toFile)
      newJar
  }

  val defaultClassesDir: Path = baseLocation.resolve("classes")
  val outputJar: Path = baseLocation.resolve("target/output.jar")
  val earlyOutput: Path = baseLocation.resolve("target/early-output.jar")
  val output: Path = if (outputToJar) outputJar else defaultClassesDir

  def defaultStoreLocation: Path = baseLocation.resolve("inc_data.zip")
  def defaultEarlyStoreLocation: Path = baseLocation.resolve("early_inc_data.zip")

  def createCompiler(
      sv: String,
      si: XScalaInstance,
      compilerBridge: Path,
      pipelining: Boolean,
      log: ManagedLogger
  ) = {
    CompilerSetup(
      sv,
      si,
      compilerBridge,
      output,
      earlyOutput,
      baseLocation,
      allSources.toVector.map(converter.toVirtualFile(_)),
      allClasspath.map(converter.toVirtualFile(_)),
      scalacOptions,
      IncOptions.of().withPipelining(pipelining),
      analysisForCp,
      defaultStoreLocation,
      defaultEarlyStoreLocation,
      converter,
      log
    )
  }

  def update(source: Path)(change: String => String): Unit = {
    val sourceFile = baseLocation.resolve(source)
    val text = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8)
    Files.write(sourceFile, change(text).getBytes(StandardCharsets.UTF_8))
    ()
  }

  def dependsOnJarFrom(other: TestProjectSetup): TestProjectSetup = {
    val sources = other.defaultClassesDir.toFile ** "*.class"
    val mapping = sources.get().map { file =>
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
  def simple(baseLocation: Path, classes: Seq[String]): TestProjectSetup = {
    val sources = Map(Paths.get("src") -> classes.map(path => Paths.get(path)))
    TestProjectSetup(baseLocation, sources, Nil, Map.empty)
  }

  def scalaCompiler(instance: XScalaInstance, bridgeJar: Path): AnalyzingCompiler = {
    val bridgeProvider = ZincUtil.constantBridgeProvider(instance, bridgeJar)
    val classpath = ClasspathOptionsUtil.boot
    val cache = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(instance, bridgeProvider, classpath, _ => (), cache)
  }

  case class CompilerSetup(
      scalaVersion: String,
      si: XScalaInstance,
      compilerBridge: Path,
      output: Path,
      earlyOutput: Path,
      tempDir: Path,
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      scalacOptions: Seq[String],
      incOptions: IncOptions,
      analysisForCp: Map[VirtualFile, Path],
      analysisStoreLocation: Path,
      earlyAnalysisStoreLocation: Path,
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
      override def advance(
          current: Int,
          total: Int,
          prevPhaseName: String,
          nextPhaseName: String
      ): Boolean = true
      override def startUnit(phase: String, unitPath: String): Unit = lastCompiledUnits += unitPath
      override def earlyOutputComplete(success: Boolean): Unit = ()
    }

    val setup = compiler.setup(
      lookup,
      skip = false,
      tempDir.resolve("inc_compile"),
      CompilerCache.fresh,
      incOptions,
      reporter,
      Some(progress),
      Some(FileAnalysisStore.getDefault(earlyAnalysisStoreLocation.toFile)),
      extra
    )

    val prev = compiler.emptyPreviousResult

    val cp = Vector(converter.toVirtualFile(output)) ++
      si.allJars.map(x => converter.toVirtualFile(x.toPath)) ++
      classpath.toVector

    val stamper = Stamps.timeWrapLibraryStamps(converter)

    val in = compiler.inputs(
      cp.toArray,
      sources.toArray,
      output,
      Some(earlyOutput),
      scalacOptions = (Vector("-Ypickle-java") ++ scalacOptions).toArray,
      javacOptions = Array(),
      maxErrors,
      sourcePositionMappers = Array(),
      CompileOrder.Mixed,
      cs,
      setup,
      prev,
      temporaryClassesDirectory = Optional.empty(),
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

    def doCompileAllJava(newInputs: Inputs => Inputs = identity): CompileResult = {
      lastCompiledUnits = Set.empty
      compiler.compileAllJava(newInputs(in), log)
    }

    def doCompileAllJavaWithStore(
        store: AnalysisStore = FileAnalysisStore.getDefault(analysisStoreLocation.toFile),
        newInputs: Inputs => Inputs = identity
    ): CompileResult = {
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
      val newResult = doCompileAllJava(in => newInputs(in.withPreviousResult(previousResult)))

      store.set(AnalysisContents.create(newResult.analysis(), newResult.setup()))
      newResult
    }

    def close(): Unit = sc.classLoaderCache.foreach(_.close())
  }

  case class MockedLookup(am: VirtualFile => Optional[CompileAnalysis])
      extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] =
      am(classpathEntry)

    override def definesClass(classpathEntry: VirtualFile): DefinesClass =
      Locate.definesClass(classpathEntry)
  }

  trait CompilerSetupHelper {
    def apply(sv: String, setup: TestProjectSetup): CompilerSetup
  }
}

object VirtualSubproject {
  case class Builder(
      base: Option[Path] = None,
      projectDeps: List[VirtualSubproject] = Nil,
      externalDeps: List[Path] = Nil,
      scalaVersion: String = scala.util.Properties.versionNumberString,
  ) {
    def baseDirectory(value: Path): Builder = copy(base = Some(value))
    def scalaVersion(value: String): Builder = copy(scalaVersion = value)
    def dependsOn(values: VirtualSubproject*): Builder =
      copy(projectDeps = this.projectDeps ::: values.toList)
    def externalDependencies(values: Path*): Builder =
      copy(externalDeps = this.externalDeps ::: values.toList)
    // call this get because "build" sounds like we're compiling something
    def get(implicit ev: TestProjectSetup.CompilerSetupHelper): VirtualSubproject =
      new VirtualSubproject(base.get, projectDeps, externalDeps, scalaVersion, ev)
  }
}

class VirtualSubproject(
    baseLocation: Path,
    projectDeps: List[VirtualSubproject],
    externalDeps: List[Path],
    scalaVersion: String,
    helper: TestProjectSetup.CompilerSetupHelper,
) {
  val setup = TestProjectSetup.simple(baseLocation, classes = Nil)
  val cp = setup.earlyOutput :: projectDeps.map(_.setup.defaultClassesDir) ::: externalDeps
  def p2vf(p: Path) = setup.converter.toVirtualFile(p)

  val analysisForCp = (this :: projectDeps).flatMap { p =>
    Seq(
      p2vf(p.setup.defaultClassesDir) -> p.setup.defaultStoreLocation,
      p2vf(p.setup.earlyOutput) -> p.setup.defaultEarlyStoreLocation,
    )
  }.toMap

  val compiler = helper(
    scalaVersion,
    setup
      .copy(analysisForCp = analysisForCp) // need setup's converter to define this, so copy in after
  ).copy(classpath = cp.map(p2vf)) // TestProjectSetup does weird CP things, so copy this in lat

  def compile(sources: VirtualFile*) = {
    val vs = sources.toArray
    compiler.doCompileWithStore(newInputs = in => in.withOptions(in.options.withSources(vs)))
  }

  def close(): Unit = compiler.close()

  def compileAllJava(sources: VirtualFile*) = {
    val vs = sources.toArray
    compiler.doCompileAllJavaWithStore(
      newInputs = in => in.withOptions(in.options.withSources(vs))
    )
  }
}
