package sbt.internal.inc

import java.io.File

import sbt.internal.util.ConsoleLogger
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.{ InterfaceUtil, Logger }
import xsbti.Maybe
import xsbti.compile._

class IncrementalCompilerSpec extends BridgeProviderSpecification {

  val scalaVersion = scala.util.Properties.versionNumberString
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val maxErrors = 100
  val knownSampleGoodFile0 =
    new File(classOf[IncrementalCompilerSpec].getResource("Good.scala").toURI)
  val fooSampleFile0 =
    new File(classOf[IncrementalCompilerSpec].getResource("Foo.scala").toURI)

  class Lookup(am: File => Maybe[CompileAnalysis]) extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Maybe[CompileAnalysis] =
      am(classpathEntry)

    override def definesClass(classpathEntry: File): DefinesClass =
      Locate.definesClass(classpathEntry)
  }

  "incremental compiler" should "compile" in {
    IO.withTemporaryDirectory { tempDir =>
      val knownSampleGoodFile = tempDir / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val compilerBridge = getCompilerBridge(tempDir, Logger.Null, scalaVersion)
      val si = scalaInstance(scalaVersion)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val lookup = new Lookup(Function.const(Maybe.nothing[CompileAnalysis]))
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val reporter = new LoggerReporter(maxErrors, log, identity)
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(lookup, skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, None, extra)
      val prev = compiler.emptyPreviousResult
      val classesDir = tempDir / "classes"
      val in = compiler.inputs(si.allJars, Array(knownSampleGoodFile), classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev)
      val result = compiler.compile(in, log)
      val expectedOuts = List(classesDir / "test" / "pkg" / "Good$.class")
      expectedOuts foreach { f => assert(f.exists, s"$f does not exist.") }
      val a = result.analysis match { case a: Analysis => a }
      assert(a.stamps.allInternalSources.nonEmpty)
    }
  }

  it should "not compile anything if source has not changed" in {
    IO.withTemporaryDirectory { tempDir =>
      val knownSampleGoodFile = tempDir / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val fooSampleFile = tempDir / "src" / "Foo.scala"
      IO.copyFile(fooSampleFile0, fooSampleFile, false)
      val sources = Array(knownSampleGoodFile, fooSampleFile)
      val log = ConsoleLogger()
      // uncomment this to see the debug log
      // log.setLevel(Level.Debug)
      val compilerBridge = getCompilerBridge(tempDir, Logger.Null, scalaVersion)
      val si = scalaInstance(scalaVersion)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val prev0 = compiler.emptyPreviousResult
      val lookup = new Lookup(_ => prev0.analysis)
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val reporter = new LoggerReporter(maxErrors, log, identity)
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(lookup, skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, None, extra)
      val classesDir = tempDir / "classes"
      val in = compiler.inputs(si.allJars, sources, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev0)
      val result = compiler.compile(in, log)
      val prev = compiler.previousResult(result)
      val lookup2 = new Lookup(_ => prev.analysis)
      val setup2 = compiler.setup(lookup2, skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, None, extra)
      val in2 = compiler.inputs(si.allJars, sources, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup2, prev)
      val result2 = compiler.compile(in2, log)
      assert(!result2.hasModified)
    }
  }

  it should "trigger full compilation if extra changes" in {
    IO.withTemporaryDirectory { tempDir =>
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore = AnalysisStore.cached(FileBasedStore(cacheFile))

      val knownSampleGoodFile = tempDir / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val fooSampleFile = tempDir / "src" / "Foo.scala"
      IO.copyFile(fooSampleFile0, fooSampleFile, false)
      val sources = Array(knownSampleGoodFile, fooSampleFile)
      val log = ConsoleLogger()
      // uncomment this to see the debug log
      // log.setLevel(Level.Debug)
      val compilerBridge = getCompilerBridge(tempDir, Logger.Null, scalaVersion)
      val si = scalaInstance(scalaVersion)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val prev0 = compiler.emptyPreviousResult
      val lookup = new Lookup(_ => prev0.analysis)
      val incOptions = IncOptionsUtil.defaultIncOptions().withApiDebug(true)
      val reporter = new LoggerReporter(maxErrors, log, identity)
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(lookup, skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, None, extra)
      val classesDir = tempDir / "classes"
      val in = compiler.inputs(si.allJars, sources, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev0)
      val result = compiler.compile(in, log)
      fileStore.set(result.analysis match { case a: Analysis => a }, result.setup)
      val prev = fileStore.get match {
        case Some((a, s)) => new PreviousResult(Maybe.just(a), Maybe.just(s))
        case _            => sys.error("previous is not found")
      }
      val lookup2 = new Lookup(_ => prev.analysis)
      val extra2 = Array(InterfaceUtil.t2(("key", "value2")))
      val setup2 = compiler.setup(lookup2, skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, None, extra2)
      val in2 = compiler.inputs(si.allJars, sources, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup2, prev)
      val result2 = compiler.compile(in2, log)
      assert(result2.hasModified)
    }
  }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(bridgeJar), ClasspathOptionsUtil.boot)
}
