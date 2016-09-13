package sbt.inc

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{ Path, Paths }

import sbt.internal.inc._
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.{ InterfaceUtil, Logger }
import xsbti.Maybe
import xsbti.compile._
import xsbti.compile.ScalaInstance

class BaseCompilerSpec extends BridgeProviderSpecification {

  val scalaVersion = scala.util.Properties.versionNumberString
  val maxErrors = 100

  case class MockedLookup(am: File => Maybe[CompileAnalysis]) extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Maybe[CompileAnalysis] =
      am(classpathEntry)

    override def definesClass(classpathEntry: File): DefinesClass =
      Locate.definesClass(classpathEntry)
  }

  case class ProjectSetup(baseLocation: Path, sources: Map[Path, Seq[Path]], classPath: Seq[Path]) {
    private def fromResource(prefix: Path)(path: Path): File = {
      val fullPath = prefix.resolve(path).toString()
      Option(getClass.getClassLoader.getResource(fullPath)).map(url => new File(url.toURI))
        .getOrElse(throw new NoSuchElementException(s"Missing resource $fullPath"))
    }

    private val sourcesPrefix = Paths.get("sources")
    private val binPrefix = Paths.get("bin")

    val allSources = for {
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

    def createCompiler() = CompilerSetup(defaultClassesDir, baseLocation.toFile, allSources.toArray, allClasspath)
  }

  object ProjectSetup {
    def simple(baseLocation: Path, classes: Seq[String]): ProjectSetup =
      ProjectSetup(baseLocation, Map(Paths.get("src") -> classes.map(path => Paths.get(path))), Nil)
  }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerBridgeProvider.constant(bridgeJar),
      ClasspathOptionsUtil.boot, _ => (), Some(new ClassLoaderCache(new URLClassLoader(Array()))))

  case class CompilerSetup(classesDir: File, tempDir: File, sources: Array[File], classpath: Seq[File]) {
    val compiler = new IncrementalCompilerImpl
    val compilerBridge = getCompilerBridge(tempDir, Logger.Null, scalaVersion)

    val si = scalaInstance(scalaVersion)
    val sc = scalaCompiler(si, compilerBridge)
    val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)

    val lookup = MockedLookup(Function.const(Maybe.nothing[CompileAnalysis]))
    val incOptions = IncOptionsUtil.defaultIncOptions()
    val reporter = new LoggerReporter(maxErrors, log, identity)
    val extra = Array(InterfaceUtil.t2(("key", "value")))

    var lastCompiledUnits: Set[String] = Set.empty
    val progress = new CompileProgress {
      override def advance(current: Int, total: Int): Boolean = true

      override def startUnit(phase: String, unitPath: String): Unit = lastCompiledUnits += unitPath
    }

    val setup = compiler.setup(lookup, skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, None, extra)
    val prev = compiler.emptyPreviousResult
    val in = compiler.inputs(si.allJars ++ classpath, sources, classesDir, Array(), Array(), maxErrors, Array(),
      CompileOrder.Mixed, cs, setup, prev)

    def doCompile(newInputs: Inputs => Inputs = identity): CompileResult = {
      lastCompiledUnits = Set.empty
      compiler.compile(newInputs(in), log)
    }

    def doCompileWithStore(store: AnalysisStore, newInputs: Inputs => Inputs = identity): CompileResult = {
      val previousResult = store.get() match {
        case Some((prevAnalysis, prevSetup)) =>
          new PreviousResult(Maybe.just[CompileAnalysis](prevAnalysis), Maybe.just[MiniSetup](prevSetup))
        case _ =>
          compiler.emptyPreviousResult
      }
      val newResult = doCompile(in => newInputs(in.withPreviousResult(previousResult)))

      store.set(newResult.analysis(), newResult.setup())
      newResult
    }
  }

}
