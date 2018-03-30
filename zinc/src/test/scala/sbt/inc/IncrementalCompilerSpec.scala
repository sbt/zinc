package sbt.inc

import xsbti.compile.AnalysisStore
import sbt.internal.inc.{ AnalysisStore => _, _ }
import sbt.io.IO
import sbt.io.syntax._

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

  it should "store the name-positions index" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()
      val result = compilerSetup.doCompile()
      val a = result.analysis match { case a: Analysis => a }
      val sourceInfo_Good = a.infos.get(tempDir / "src" / SourceFiles.Good)
      val usedNames_Good = sourceInfo_Good.getUsedNamePositions.toSet
      val definedNames_Good = sourceInfo_Good.getDefinedNamePositions.toSet
      val sourceInfo_Foo = a.infos.get(tempDir / "src" / SourceFiles.Foo)
      val usedNames_Foo = sourceInfo_Foo.getUsedNamePositions.toSet
      val definedNames_Foo = sourceInfo_Foo.getDefinedNamePositions.toSet

      assert(
        usedNames_Good == Set(
          NamePosition(1, 9, "pkg", "pkg"),
          NamePosition(3, 21, "App", "scala.App"),
          NamePosition(6, 3, "scala", "scala"),
          NamePosition(6, 9, "collection", "scala.collection"),
          NamePosition(6, 20, "immutable", "scala.collection.immutable"),
          // It is difficult to judge whether `apply` is omitted
          // NamePosition(6, 30, "List", "scala.collection.immutable.List.apply"),
          NamePosition(6, 30, "apply", "scala.collection.immutable.List.apply"),
          NamePosition(7, 3, "println", "scala.Predef.println")
        )
      )
      assert(
        definedNames_Good == Set(NamePosition(3, 8, "Good", "pkg.Good"),
                                 NamePosition(5, 7, "x", "pkg.Good.x")))
      assert(
        usedNames_Foo == Set(NamePosition(1, 9, "pkg", "pkg"),
                             NamePosition(5, 3, "AnyRef", "scala.AnyRef"))
      )
      assert(
        definedNames_Foo == Set(NamePosition(3, 8, "Foo", "pkg.Foo"),
                                NamePosition(4, 7, "x", "pkg.Foo.x"))
      )
    }
  }

  it should "not store the name-positions index if `IncOptions.storeNamePositions` is set to false" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val baseCompilerSetup = projectSetup.createCompiler()
      val compilerSetup = baseCompilerSetup.copy(
        incOptions = baseCompilerSetup.incOptions.withStoreApis(false)) // TODO: use other option
      val result = compilerSetup.doCompile()
      val a = result.analysis match { case a: Analysis => a }
      val sourceInfo_Good = a.infos.get(tempDir / "src" / SourceFiles.Good)
      val usedNames_Good = sourceInfo_Good.getUsedNamePositions.toSet
      val definedNames_Good = sourceInfo_Good.getDefinedNamePositions.toSet
      val sourceInfo_Foo = a.infos.get(tempDir / "src" / SourceFiles.Foo)
      val usedNames_Foo = sourceInfo_Foo.getUsedNamePositions.toSet
      val definedNames_Foo = sourceInfo_Foo.getDefinedNamePositions.toSet

      assert(usedNames_Good.isEmpty)
      assert(definedNames_Good.isEmpty)
      assert(usedNames_Foo.isEmpty)
      assert(definedNames_Foo.isEmpty)
    }
  }

}
