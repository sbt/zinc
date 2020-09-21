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
import java.nio.file.{ Files, Path, Paths }
import java.util.Optional

import sbt.internal.inc._
import sbt.internal.inc.JavaInterfaceUtil.EnrichOption
import sbt.internal.inc.JavaInterfaceUtil.EnrichOptional
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.util.ManagedLogger
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.InterfaceUtil

import xsbti.{ FileConverter, VirtualFile }
import xsbti.compile.{ ScalaInstance => XScalaInstance, _ }
import xsbti.compile.FileAnalysisStore

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
  var lastCompiledUnits = Set.empty[String]

  val progress = new CompileProgress {
    override def startUnit(phase: String, unitPath: String): Unit = lastCompiledUnits += unitPath
  }

  val perClasspathEntryLookup = new PerClasspathEntryLookup {
    def read(p: Path) = FileAnalysisStore.getDefault(p.toFile).get().toOption.map(_.getAnalysis)
    def analysis(cpEntry: VirtualFile) = analysisForCp.get(cpEntry).flatMap(read).toOptional
    def definesClass(cpEntry: VirtualFile) = Locate.definesClass(cpEntry)
  }

  val maxErrors = 100
  val zinc = new IncrementalCompilerImpl
  val bridgeProv = ZincUtil.constantBridgeProvider(si, compilerBridge)
  val boot = ClasspathOptionsUtil.boot
  val loaderCache = new ClassLoaderCache(new URLClassLoader(Array()))
  val scalac = new AnalyzingCompiler(si, bridgeProv, boot, _ => (), Some(loaderCache))
  def toVf(p: Path) = converter.toVirtualFile(p)

  val setup = zinc.setup(
    perClasspathEntryLookup,
    false,
    tempDir.resolve("inc_compile"),
    CompilerCache.fresh,
    incOptions,
    new ManagedLoggedReporter(maxErrors, log, VirtualFileUtil.sourcePositionMapper(converter)),
    Some(progress),
    Some(FileAnalysisStore.getDefault(earlyAnalysisStoreLocation.toFile)),
    Array(InterfaceUtil.t2(("key", "value")))
  )

  val in = zinc.inputs(
    (output +: si.allJars.map(_.toPath)).map(toVf) ++ classpath,
    sources.toArray,
    output,
    Some(earlyOutput),
    Array("-Ypickle-java", "-Ypickle-write", earlyOutput.toString) ++ scalacOptions,
    Array(),
    maxErrors,
    Array(),
    CompileOrder.Mixed,
    zinc.compilers(si, ClasspathOptionsUtil.boot, None, scalac),
    setup,
    zinc.emptyPreviousResult,
    Optional.empty(),
    converter,
    Stamps.timeWrapBinaryStamps(converter)
  )

  def doCompile(newInputs: Inputs => Inputs = identity): CompileResult = {
    lastCompiledUnits = Set.empty
    zinc.compile(newInputs(in), log)
  }

  def doCompileAllJava(newInputs: Inputs => Inputs = identity): CompileResult = {
    lastCompiledUnits = Set.empty
    zinc.compileAllJava(newInputs(in), log)
  }

  def doCompileWithStore(
      store: AnalysisStore = FileAnalysisStore.getDefault(analysisStoreLocation.toFile),
      newInputs: Inputs => Inputs = identity
  ): CompileResult = doCompileWithStoreImpl(doCompile, store, newInputs)

  def doCompileAllJavaWithStore(
      store: AnalysisStore = FileAnalysisStore.getDefault(analysisStoreLocation.toFile),
      newInputs: Inputs => Inputs = identity
  ): CompileResult = doCompileWithStoreImpl(doCompileAllJava, store, newInputs)

  def compile(sources: VirtualFile*) = {
    doCompileWithStore(newInputs = withSrcs(sources.toArray))
  }

  def compileAllJava(sources: VirtualFile*) = {
    doCompileAllJavaWithStore(newInputs = withSrcs(sources.toArray))
  }

  def compileBoth(sources: VirtualFile*) = {
    val res = compile(sources: _*)
    if (res.hasModified) compileAllJava(sources: _*) else res
  }

  def withSrcs(vs: Array[VirtualFile]) = (in: Inputs) => in.withOptions(in.options.withSources(vs))

  private def doCompileWithStoreImpl(
      doCompile: (Inputs => Inputs) => CompileResult,
      store: AnalysisStore,
      newInputs: Inputs => Inputs,
  ): CompileResult = {
    val prevRes = store.get().toOption.fold(zinc.emptyPreviousResult)(zinc.previousResult(_))
    val newResult = doCompile(inputs => newInputs(inputs.withPreviousResult(prevRes)))
    store.set(AnalysisContents.create(newResult.analysis, newResult.setup))
    newResult
  }

  def close(): Unit = loaderCache.close()
}

case class ProjectPaths(baseDir: Path) {}

object ProjectSetup {

  def simple(baseDir: Path, classes: Seq[String]): ProjectSetup = {
    val sources = Map(Paths.get("src") -> classes.map(Paths.get(_)))
    ProjectSetup(VirtualSubproject(baseDir), sources, Nil, Map.empty)
  }

}

case class ProjectSetup(
    proj: VirtualSubproject,
    sources: Map[Path, Seq[Path]],
    classPath: Seq[Path],
    analysisForCp: Map[VirtualFile, Path] = Map.empty,
    outputToJar: Boolean = false,
    subproject: String = "unnamed",
    scalacOptions: Seq[String] = Nil,
    overrideCp: Option[Seq[Path]] = None, // to avoid "fromResource"
) {
  def baseDir = proj.baseDir
  def converter = proj.converter
  def classesDir = proj.classesDir
  def outputJar = proj.outputJar
  def earlyOutput = proj.earlyOutput
  def analysisPath = proj.analysisPath
  def earlyAnalysisPath = proj.earlyAnalysisPath

  val output: Path = if (outputToJar) outputJar else classesDir

  val allSources: Iterable[Path] = for ((dstFile, srcFiles) <- sources; srcFile <- srcFiles) yield {
    if (srcFile.toString == "") sys.error("unexpected blank sourceFile")
    withPath(baseDir.resolve(dstFile).resolve(srcFile)) { f =>
      baseDir.resolve(srcFile) match {
        case p if Files.exists(p) => IO.copyFile(p.toFile, f)
        case _                    => IO.copyFile(fromResource(Paths.get("sources"), srcFile), f)
      }
    }
  }

  val allClasspath: Seq[Path] = overrideCp.getOrElse(classPath.map {
    case path if path.toString.endsWith(".zip") =>
      val target = baseDir.resolve(s"bin/$path".stripSuffix(".zip"))
      withPath(target)(f => IO.unzip(fromResource(Paths.get("bin"), path), f))
    case path if Files.exists(path) =>
      path
    case path =>
      val newJar = baseDir.resolve(s"bin/$path")
      withPath(newJar)(f => IO.copyFile(fromResource(Paths.get("bin"), path), f))
  })

  private def withPath[U](p: Path)(f: File => U): Path = { f(p.toFile); p }

  private def fromResource(prefix: Path, path: Path): File = {
    val fullPath = prefix.resolve(path).toString
    getClass.getClassLoader.getResource(fullPath) match {
      case null => sys.error(s"path = '$path' ($fullPath) not found")
      case url  => new File(url.toURI)
    }
  }

  def createCompiler(
      sv: String,
      si: XScalaInstance,
      compilerBridge: Path,
      pipelining: Boolean,
      log: ManagedLogger,
  ): CompilerSetup = {
    CompilerSetup(
      sv,
      si,
      compilerBridge,
      output,
      earlyOutput,
      baseDir,
      allSources.toVector.map(converter.toVirtualFile(_)),
      allClasspath.map(converter.toVirtualFile(_)),
      scalacOptions,
      IncOptions.of().withPipelining(pipelining),
      analysisForCp,
      analysisPath,
      earlyAnalysisPath,
      converter,
      log
    )
  }

}

case class VirtualSubproject(
    baseDir: Path,
    projectDeps: List[VirtualSubproject] = Nil,
    externalDeps: List[Path] = Nil,
) {
  private val sbtBoot = Paths.get(sys.props("user.home")).resolve(".sbt/boot")
  private val javaHome = Paths.get(sys.props("java.home"))
  private val rootPaths = Map("BASE" -> baseDir, "SBT_BOOT" -> sbtBoot, "JAVA_HOME" -> javaHome)

  val converter = new MappedFileConverter(rootPaths, allowMachinePath = true)

  val classesDir = baseDir.resolve("classes")
  val outputJar = baseDir.resolve("target/output.jar")
  val earlyOutput = baseDir.resolve("target/early-output.jar")
  val analysisPath = baseDir.resolve("inc_data.zip")
  val earlyAnalysisPath = baseDir.resolve("early_inc_data.zip")

  def dependsOn(values: VirtualSubproject*) = copy(projectDeps = projectDeps ++ values)
  def extDeps(values: Path*) = copy(externalDeps = externalDeps ++ values)
  def toVf(p: Path) = converter.toVirtualFile(p)

  val analysisSelf = Map(toVf(classesDir) -> analysisPath, toVf(earlyOutput) -> earlyAnalysisPath)
  val analysisForCp = (this :: projectDeps).flatMap(_.analysisSelf).toMap
  val cp = earlyOutput :: projectDeps.map(_.classesDir) ::: externalDeps
  val setup = ProjectSetup(this, Map.empty, Nil, analysisForCp, overrideCp = Some(cp))
}
