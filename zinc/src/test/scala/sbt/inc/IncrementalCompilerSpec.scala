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

package sbt.inc

import xsbti.compile.AnalysisStore
import sbt.internal.inc._
import sbt.io.IO
import sbt.io.syntax._
import java.nio.file.Files

class IncrementalCompilerSpec extends BaseCompilerSpec {
  //override val logLevel = sbt.util.Level.Debug

  "incremental compiler" should "compile" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good))
      val compiler = projectSetup.createCompiler()
      try {
        val result = projectSetup.createCompiler().doCompile()
        val expectedOuts =
          List(projectSetup.defaultClassesDir.resolve("pkg").resolve("Good$.class"))
        expectedOuts foreach { f =>
          assert(Files.exists(f), s"$f does not exist.")
        }
        val a = result.analysis match {
          case a: Analysis => a
        }
        assert(a.stamps.allSources.nonEmpty)
      } finally {
        compiler.close()
      }
    }
  }

  it should "not compile anything if source has not changed" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()
      try {

        val result = compilerSetup.doCompile()
        val result2 =
          compilerSetup.doCompile(
            _.withPreviousResult(compilerSetup.compiler.previousResult(result))
          )

        assert(!result2.hasModified)
      } finally {
        compilerSetup.close()
      }
    }
  }

  it should "compile Java code" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.NestedJavaClasses))

      val compiler = projectSetup.createCompiler()
      try {
        val result = compiler.doCompile()
        val expectedOuts = List(projectSetup.defaultClassesDir.resolve("NestedJavaClasses.class"))
        expectedOuts foreach { f =>
          assert(Files.exists(f), s"$f does not exist.")
        }
        val a = result.analysis match {
          case a: Analysis => a
        }
        assert(a.stamps.allSources.nonEmpty)
      } finally {
        compiler.close()
      }
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
        compilerSetup.doCompileWithStore(
          fileStore,
          _.withSetup(compilerSetup.setup.withExtra(Array()))
        )
      assert(result3.hasModified)
    }
  }
}
