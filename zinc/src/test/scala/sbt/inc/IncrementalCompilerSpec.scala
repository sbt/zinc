package sbt
package inc

import java.io.File

import sbt.internal.inc._
import sbt.io.{ IO, PathFinder }
import sbt.io.Path._
import sbt.util.{ Logger, InterfaceUtil, Level }
import sbt.util.InterfaceUtil.f1
import xsbti.{ F1, Maybe }
import xsbti.compile.{ Compilers, CompileAnalysis, CompileOrder, DefinesClass, IncOptions, IncOptionsUtil, PreviousResult, TransactionalManagerType }

class IncrementalCompilerSpec extends BridgeProviderSpecification {
  // uncomment this to see the debug log
  // log.setLevel(Level.Debug)

  val scalaVersion = scala.util.Properties.versionNumberString
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val maxErrors = 100
  val knownSampleGoodFile0 =
    new File(classOf[IncrementalCompilerSpec].getResource("Good.scala").toURI)
  val fooSampleFile0 =
    new File(classOf[IncrementalCompilerSpec].getResource("Foo.scala").toURI)
  val badSampleFile0 =
    new File(classOf[IncrementalCompilerSpec].getResource("Bad.scala").toURI)
  val dc = f1[File, DefinesClass] { f =>
    val x = Locate.definesClass(f)
    new DefinesClass {
      override def apply(className: String): Boolean = x(className)
    }
  }
  val reporter = new LoggerReporter(maxErrors, log, identity)

  case class Harness(
    tempDir: File,
    si: ScalaInstance,
    cs: Compilers
  ) {
    def classesDir: File = tempDir / "classes"
  }

  def withHarness(body: Harness => Unit): Unit =
    IO.withTemporaryDirectory { tempDir =>
      val compilerBridge = getCompilerBridge(tempDir, Logger.Null, scalaVersion)
      val si = scalaInstance(scalaVersion)
      val sc = new AnalyzingCompiler(si, CompilerInterfaceProvider.constant(compilerBridge), ClasspathOptions.boot)
      val cs = compiler.compilers(si, ClasspathOptions.boot, None, sc)
      body(Harness(tempDir, si, cs))
    }

  "incremental compiler" should "compile" in {
    withHarness { h =>
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val knownSampleGoodFile = h.tempDir / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val analysisMap = f1((f: File) => Maybe.nothing[CompileAnalysis])
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(analysisMap, dc, skip = false, h.tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val prev = compiler.emptyPreviousResult
      val in = compiler.inputs(h.si.allJars, Array(knownSampleGoodFile), h.classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, h.cs, setup, prev)
      val result = compiler.compile(in, log)
      val expectedOuts = List(h.classesDir / "test" / "pkg" / "Good$.class")
      expectedOuts foreach { f => assert(f.exists, s"$f does not exist.") }
      val a = result.analysis match { case a: Analysis => a }
      assert(a.stamps.allInternalSources.nonEmpty)
    }
  }

  it should "not compile anything if source has not changed" in {
    withHarness { h =>
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val knownSampleGoodFile = h.tempDir / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val fooSampleFile = h.tempDir / "src" / "Foo.scala"
      IO.copyFile(fooSampleFile0, fooSampleFile, false)
      val sources = Array(knownSampleGoodFile, fooSampleFile)
      val prev0 = compiler.emptyPreviousResult
      val analysisMap = f1((f: File) => prev0.analysis)
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(analysisMap, dc, skip = false, h.tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val in = compiler.inputs(h.si.allJars, sources, h.classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, h.cs, setup, prev0)
      val result = compiler.compile(in, log)
      val prev = compiler.previousResult(result)
      val analysisMap2 = f1((f: File) => prev.analysis)
      val setup2 = compiler.setup(analysisMap2, dc, skip = false, h.tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val in2 = compiler.inputs(h.si.allJars, sources, h.classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, h.cs, setup2, prev)
      val result2 = compiler.compile(in2, log)
      assert(!result2.hasModified)
    }
  }

  it should "trigger full compilation if extra changes" in {
    withHarness { h =>
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val cacheFile = h.tempDir / "target" / "inc_compile"
      val fileStore = AnalysisStore.cached(FileBasedStore(cacheFile))

      val knownSampleGoodFile = h.tempDir / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val fooSampleFile = h.tempDir / "src" / "Foo.scala"
      IO.copyFile(fooSampleFile0, fooSampleFile, false)
      val sources = Array(knownSampleGoodFile, fooSampleFile)
      val prev0 = compiler.emptyPreviousResult
      val analysisMap = f1((f: File) => prev0.analysis)
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(analysisMap, dc, skip = false, h.tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val in = compiler.inputs(h.si.allJars, sources, h.classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, h.cs, setup, prev0)
      val result = compiler.compile(in, log)
      //val prev = compiler.previousResult(result)
      fileStore.set(result.analysis match { case a: Analysis => a }, result.setup)
      val prev = fileStore.get match {
        case Some((a, s)) => new PreviousResult(Maybe.just(a), Maybe.just(s))
        case _            => sys.error("previous is not found")
      }
      val analysisMap2 = f1((f: File) => prev.analysis)
      val extra2 = Array(InterfaceUtil.t2(("key", "value2")))
      val setup2 = compiler.setup(analysisMap2, dc, skip = false, h.tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra2)
      val in2 = compiler.inputs(h.si.allJars, sources, h.classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, h.cs, setup2, prev)
      val result2 = compiler.compile(in2, log)
      assert(result2.hasModified)
    }
  }

  it should "restore existing classfiles on failure" in {
    withHarness { h =>
      def currentClasses() = (PathFinder(h.classesDir) ** "*.class").get.toSet
      val incOptions =
        IncOptionsUtil.defaultIncOptions()
          .withClassfileManagerType(
            Maybe.just(new TransactionalManagerType(h.tempDir / "backup", log))
          )
      val knownSampleGoodFile = h.tempDir / "src" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val sources = Array(knownSampleGoodFile)
      val prev0 = compiler.emptyPreviousResult
      val analysisMap = f1((f: File) => prev0.analysis)
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(analysisMap, dc, skip = false, h.tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val in = compiler.inputs(h.si.allJars, sources, h.classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, h.cs, setup, prev0)
      val result = compiler.compile(in, log)
      val prev = compiler.previousResult(result)
      val expectedClasses = currentClasses()

      // Compile again with an analysis function that has a sideeffect of destroying
      // classes, and ensure that the classes directory is restored afterward.
      val exception = new Exception("Expected exception")
      val analysisMap2 =
        f1[File, Maybe[CompileAnalysis]] { f =>
          expectedClasses.foreach(_.delete())
          throw exception
        }

      val badSampleFile = h.tempDir / "src" / "Foo.scala"
      IO.copyFile(badSampleFile0, badSampleFile, false)
      val sources2 = Array(knownSampleGoodFile)
      val setup2 = compiler.setup(analysisMap2, dc, skip = false, h.tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val in2 = compiler.inputs(h.si.allJars, sources2, h.classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, h.cs, setup2, prev)

      // Expect the compile to have been poisoned.
      try {
        val result = compiler.compile(in2, log)
        assert(false, s"Expected to fail while compiling $badSampleFile")
      } catch {
        case e if e == exception =>
        // pass
      }

      // Confirm that the original classfiles are still in place.
      assert(expectedClasses == currentClasses())
    }
  }
}
