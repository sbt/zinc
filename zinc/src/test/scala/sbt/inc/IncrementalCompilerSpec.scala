package sbt.inc

import xsbti.compile.AnalysisStore
import sbt.internal.inc.{ AnalysisStore => _, _ }
import sbt.io.IO
import sbt.io.syntax._
import xsbti.semanticdb3.{ Range, Role, SymbolOccurrence }

class IncrementalCompilerSpec extends BaseCompilerSpec {

  "incremental compiler" should "compile" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good))

      val result = projectSetup.createCompiler().doCompile()
      val expectedOuts = List(projectSetup.defaultClassesDir / "pkg" / "Good$.class")
      expectedOuts foreach { f =>
        assert(f.exists, s"$f does not exist.")
      }
      val a = result.analysis match { case a: Analysis => a }
      assert(a.stamps.allSources.nonEmpty)
    }
  }

  it should "not compile anything if source has not changed" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()

      val result = compilerSetup.doCompile()
      val result2 =
        compilerSetup.doCompile(_.withPreviousResult(compilerSetup.compiler.previousResult(result)))

      assert(!result2.hasModified)
    }
  }

  it should "trigger full compilation if extra changes" in {
    IO.withTemporaryDirectory { tempDir =>
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore0 = FileAnalysisStore.binary(cacheFile)
      val fileStore = AnalysisStore.getCachedStore(fileStore0)

      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()

      val result = compilerSetup.doCompileWithStore(fileStore)
      assert(result.hasModified)

      val result2 = compilerSetup.doCompileWithStore(fileStore)
      assert(!result2.hasModified)

      val result3 =
        compilerSetup.doCompileWithStore(fileStore,
                                         _.withSetup(compilerSetup.setup.withExtra(Array())))
      assert(result3.hasModified)
    }
  }

  it should "store an index of SymbolOccurrence" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()
      val result = compilerSetup.doCompile()
      val a = result.analysis match { case a: Analysis => a }
      val sourceInfo_Good = a.infos.get(tempDir / "src" / SourceFiles.Good)
      val symbolOccurrences_Good = sourceInfo_Good.getSymbolOccurrences.toSet
      val sourceInfo_Foo = a.infos.get(tempDir / "src" / SourceFiles.Foo)
      val symbolOccurrences_Foo = sourceInfo_Foo.getSymbolOccurrences.toSet

      assert(symbolOccurrences_Good
        .filterNot(so =>
          so.symbol == "scala.AnyRef#" || so.symbol == "scala.Any#" || so.symbol == "scala.Nothing#")
        .filterNot(_.symbol == "pkg.Good#`<init>`().") ===
        // The primary constructor differs in Position depending on a scala version
        Set(
          SymbolOccurrence.of(Range.of(2, 7, 2, 11), "pkg.Good.", Role.DEFINITION),
          SymbolOccurrence.of(Range.of(2, 20, 2, 23), "scala.App#", Role.REFERENCE),
          SymbolOccurrence.of(Range.of(4, 2, 4, 9), "scala.Predef#println(Any).", Role.REFERENCE)
        ))
      assert(
        symbolOccurrences_Foo
          .filterNot(so =>
            so.symbol == "scala.AnyRef#" || so.symbol == "scala.Any#" || so.symbol == "scala.Nothing#")
          .filterNot(_.symbol == "pkg.Foo#`<init>`().") ===
          Set(
            SymbolOccurrence.of(Range.of(2, 7, 2, 10), "pkg.Foo.", Role.DEFINITION),
            SymbolOccurrence.of(Range.of(3, 6, 3, 8), "pkg.Foo#x.", Role.DEFINITION),
            SymbolOccurrence.of(Range.of(3, 6, 3, 7), "pkg.Foo#x().", Role.DEFINITION)
          )
      )
    }
  }

  it should "not store an index of SymbolOccurrence if `IncOptions.storeNamePositions` is set to false" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val baseCompilerSetup = projectSetup.createCompiler()
      val compilerSetup = baseCompilerSetup.copy(
        incOptions = baseCompilerSetup.incOptions.withStoreSymbolIndex(false))
      val result = compilerSetup.doCompile()
      val a = result.analysis match { case a: Analysis => a }
      val sourceInfo_Good = a.infos.get(tempDir / "src" / SourceFiles.Good)
      val symbolOccurrences_Good = sourceInfo_Good.getSymbolOccurrences.toSet
      val sourceInfo_Foo = a.infos.get(tempDir / "src" / SourceFiles.Foo)
      val symbolOccurrences_Foo = sourceInfo_Foo.getSymbolOccurrences.toSet

      assert(symbolOccurrences_Good.isEmpty)
      assert(symbolOccurrences_Foo.isEmpty)
    }
  }

}
