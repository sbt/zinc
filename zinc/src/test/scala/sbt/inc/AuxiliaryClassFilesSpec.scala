/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.inc

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.FileAnalysisStore
import sbt.io.IO
import sbt.io.syntax.*
import xsbti.compile.{
  AnalysisStore,
  AuxiliaryClassFiles,
  ScalaNativeFiles,
  TastyFiles,
  TransactionalManagerType
}

import java.io.File
import java.nio.file.Files

object AuxiliaryClassFilesSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example(
      "incremental compiler with auxiliary class files should remove auxiliary tasty files",
      removesTasty(Array(TastyFiles.instance()))
    ),
    example("ignore non-existent auxiliary files", ignoresNonExistent),
    example(
      "handle auxiliary files reported twice",
      removesTasty(
        // tastyFiles reported twice
        Array(TastyFiles.instance(), TastyFiles.instance())
      )
    ),
  )

  private def withAuxiliaryCompiler(tempDir: File, auxiliary: Array[AuxiliaryClassFiles])(
      f: (ProjectSetup, CompilerSetup, AnalysisStore) => Result
  ): Result =
    val setup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
    val options = incOptions
      .withAuxiliaryClassFiles(auxiliary)
      .withClassfileManagerType(
        TransactionalManagerType.of(tempDir / "classes.back", sbt.util.Logger.Null)
      )
    withCompiler(setup.createCompiler().copy(incOptions = options)) { compiler =>
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      f(setup, compiler, AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile)))
    }

  private def removesTasty(auxiliary: Array[AuxiliaryClassFiles]): Result =
    IO.withTemporaryDirectory { tempDir =>
      withAuxiliaryCompiler(tempDir, auxiliary) { (setup, compiler, fileStore) =>
        compiler.doCompileWithStore(fileStore)

        // create fake Foo.tasty files
        val fooTastyFile = setup.classesDir.resolve("pkg/Foo.tasty")
        IO.touch(fooTastyFile.toFile)

        // remove Foo.scala
        IO.delete(setup.baseDir.resolve(s"src/${SourceFiles.Foo}").toFile)

        // recompile
        val goodSrcFile = setup.baseDir.resolve(s"src/${SourceFiles.Good}")
        val newSources = Array(setup.converter.toVirtualFile(goodSrcFile))
        compiler.doCompileWithStore(
          fileStore,
          i => i.withOptions(i.options().withSources(newSources))
        )
        Result.assert(!Files.exists(fooTastyFile)).log(s"$fooTastyFile still exists")
      }
    }

  private def ignoresNonExistent: Result = IO.withTemporaryDirectory { tempDir =>
    withAuxiliaryCompiler(tempDir, Array(ScalaNativeFiles.instance())) {
      (_, compiler, fileStore) =>
        val res = compiler.doCompileWithStore(fileStore)

        // recompile
        val res1 = compiler.doCompileWithStore(
          fileStore,
          i => i.withSetup(i.setup().withExtra(Array()))
        )
        Result.all(List(Result.assert(res.hasModified), Result.assert(res1.hasModified)))
    }
  }
end AuxiliaryClassFilesSpec
