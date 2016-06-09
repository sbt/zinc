package sbt
package inc

import java.io.File

import sbt.internal.inc._
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.{ Logger, InterfaceUtil }
import sbt.internal.util.ConsoleLogger
import xsbti.Maybe
import xsbti.compile.{ CompileAnalysis, CompileOrder, DefinesClass, IncOptionsUtil, PreviousResult, PerClasspathEntryLookup }

class WatcherSpec extends BridgeProviderSpecification {
  val scalaVersion = scala.util.Properties.versionNumberString
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val maxErrors = 100
  val knownSampleGoodFile0 =
    new File(classOf[IncrementalCompilerSpec].getResource("Good.scala").toURI)
  val fooSampleFile0 =
    new File(classOf[IncrementalCompilerSpec].getResource("Foo.scala").toURI)
  val fooSampleFile2 =
    new File(classOf[IncrementalCompilerSpec].getResource("Foo2.scala").toURI)

  class Lookup(am: File => Maybe[CompileAnalysis]) extends PerClasspathEntryLookup {
    override def analysis(classpathEntry: File): Maybe[CompileAnalysis] =
      am(classpathEntry)

    override def definesClass(classpathEntry: File): DefinesClass =
      Locate.definesClass(classpathEntry)
  }

  it should "use native file watcher to detect modified files" in {
    IO.withTemporaryDirectory { tempDir =>
      val log = ConsoleLogger()
      // uncomment this to see the debug log
      // log.setLevel(Level.Debug)
      val sourceDirectory = tempDir / "src"
      IO.createDirectory(sourceDirectory)
      val fileWatch = IncrementalCompilerUtil.fileWatch(List(sourceDirectory), log)
      Thread.sleep(100)
      val fooSampleFile = tempDir / "src" / "Foo.scala"
      IO.copyFile(fooSampleFile0, fooSampleFile, false)
      val knownSampleGoodFile = tempDir / "src" / "subdir" / "Good.scala"
      IO.copyFile(knownSampleGoodFile0, knownSampleGoodFile, false)
      val sources = Array(fooSampleFile, knownSampleGoodFile)
      val compilerBridge = getCompilerBridge(tempDir, Logger.Null, scalaVersion)
      val si = scalaInstance(scalaVersion)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptionsUtil.boot, None, sc)
      val prev0 = compiler.emptyPreviousResult
      val lookup = new Lookup(_ => prev0.analysis)
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val reporter = new LoggerReporter(maxErrors, log, identity)
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(lookup, fileWatch,
        skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val classesDir = tempDir / "classes"
      val in = compiler.inputs(si.allJars, sources, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev0)
      // This is the baseline compilation using two files
      // At this point there's no good previous end change time,
      // so the watcher will return None, and fallback to checking last modified dates
      log.info("---- compile 1 ----")
      val result1 = compiler.compile(in, log)

      // Here we introduce a change trackable by the native OS
      IO.copyFile(fooSampleFile2, fooSampleFile, false)
      Thread.sleep(100)
      val sources2 = Array(knownSampleGoodFile, fooSampleFile)
      val prev2 = compiler.previousResult(result1)
      val lookup2 = new Lookup(_ => prev2.analysis)
      val setup2 = compiler.setup(lookup2, fileWatch,
        skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val in2 = compiler.inputs(si.allJars, sources2, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup2, prev2)
      Thread.sleep(100)
      log.info("---- compile 2 ----")
      val result2 = compiler.compile(in2, log)
      assert(result2.hasModified)

      // This is for no-op compilation
      // it should not detect anything
      val prev3 = compiler.previousResult(result2)
      val lookup3 = new Lookup(_ => prev3.analysis)
      val setup3 = compiler.setup(lookup3, fileWatch,
        skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val in3 = compiler.inputs(si.allJars, sources2, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup3, prev3)
      Thread.sleep(100)
      log.info("---- compile 3 ----")
      val result3 = compiler.compile(in3, log)
      assert(!result3.hasModified)
    }
  }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(bridgeJar), ClasspathOptionsUtil.boot)
}
