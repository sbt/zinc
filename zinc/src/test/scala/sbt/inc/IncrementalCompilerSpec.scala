package sbt
package inc

import java.io.File
import java.net.URLClassLoader

import sbt.internal.inc._
import sbt.io.IO
import sbt.io.syntax._
import sbt.util.{ Logger, InterfaceUtil }
import sbt.internal.util.ConsoleLogger
import xsbti.Maybe
import xsbti.compile.{ CompileAnalysis, CompileOrder, DefinesClass, IncOptionsUtil, PreviousResult, PerClasspathEntryLookup }
import sbt.internal.inc.classpath.ClassLoaderCache

class IncrementalCompilerSpec extends BaseCompilerSpec {

  "incremental compiler" should "compile" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good))

      val result = projectSetup.createCompiler().doCompile()
      val expectedOuts = List(projectSetup.defaultClassesDir / "pkg" / "Good$.class")
      expectedOuts foreach { f => assert(f.exists, s"$f does not exist.") }
      val a = result.analysis match { case a: Analysis => a }
      assert(a.stamps.allInternalSources.nonEmpty)
    }
  }

  it should "not compile anything if source has not changed" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()

      val result = compilerSetup.doCompile()
      val result2 = compilerSetup.doCompile(_.withPreviousResult(compilerSetup.compiler.previousResult(result)))

      assert(!result2.hasModified)
    }
  }

  it should "trigger full compilation if extra changes" in {
    IO.withTemporaryDirectory { tempDir =>
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore = AnalysisStore.cached(FileBasedStore(cacheFile))

      val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()

      val result = compilerSetup.doCompileWithStore(fileStore)
      assert(result.hasModified)

      val result2 = compilerSetup.doCompileWithStore(fileStore)
      assert(!result2.hasModified)

      val result3 = compilerSetup.doCompileWithStore(fileStore, _.withSetup(compilerSetup.setup.withExtra(Array())))
      assert(result3.hasModified)
    }
  }

}
