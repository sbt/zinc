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

import sbt.internal.inc.FileAnalysisStore
import sbt.io.IO
import sbt.io.syntax._
import xsbti.compile.{ AnalysisStore, ScalaNativeFiles, TastyFiles, TransactionalManagerType }

import java.nio.file.Files

class AuxiliaryClassFilesSpec extends BaseCompilerSpec {
  behavior.of("incremental compiler with auxiliary class files")
  it should "remove auxiliary tasty files" in IO.withTemporaryDirectory { tempDir =>
    val classes = Seq(SourceFiles.Good, SourceFiles.Foo)
    val setup = ProjectSetup.simple(tempDir.toPath, classes)
    val backup = tempDir / "classes.back"
    val options = incOptions
      .withAuxiliaryClassFiles(Array(TastyFiles.instance()))
      .withClassfileManagerType(TransactionalManagerType.of(backup, sbt.util.Logger.Null))
    val compiler = setup.createCompiler().copy(incOptions = options)
    try {
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile))

      compiler.doCompileWithStore(fileStore)

      // create fake Foo.tasty files
      val fooTastyFile = setup.classesDir.resolve("pkg/Foo.tasty")
      IO.touch(fooTastyFile.toFile)

      // remove Foo.scala
      val fooSrcFile = setup.baseDir.resolve(s"src/${SourceFiles.Foo}")
      IO.delete(fooSrcFile.toFile)

      // recompile
      val goodSrcFile = setup.baseDir.resolve(s"src/${SourceFiles.Good}")
      val newSources = Array(setup.converter.toVirtualFile(goodSrcFile))
      compiler.doCompileWithStore(
        fileStore,
        i => i.withOptions(i.options().withSources(newSources))
      )
      assert(!Files.exists(fooTastyFile))
    } finally compiler.close()
  }

  it should "ignore non-existent auxiliary files" in IO.withTemporaryDirectory { tempDir =>
    val classes = Seq(SourceFiles.Good, SourceFiles.Foo)
    val setup = ProjectSetup.simple(tempDir.toPath, classes)
    val backup = tempDir / "classes.back"
    val options = incOptions
      .withAuxiliaryClassFiles(Array(ScalaNativeFiles.instance()))
      .withClassfileManagerType(TransactionalManagerType.of(backup, sbt.util.Logger.Null))
    val compiler = setup.createCompiler().copy(incOptions = options)
    try {
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile))

      val res = compiler.doCompileWithStore(fileStore)
      assert(res.hasModified)

      // recompile
      val res1 = compiler.doCompileWithStore(
        fileStore,
        i => i.withSetup(i.setup().withExtra(Array()))
      )
      assert(res1.hasModified)
    } finally compiler.close()
  }

  it should "handle auxiliary files reported twice" in IO.withTemporaryDirectory { tempDir =>
    val classes = Seq(SourceFiles.Good, SourceFiles.Foo)
    val setup = ProjectSetup.simple(tempDir.toPath, classes)
    val backup = tempDir / "classes.back"
    val options = incOptions
      .withAuxiliaryClassFiles(
        // tastyFiles reported twice
        Array(TastyFiles.instance(), TastyFiles.instance())
      )
      .withClassfileManagerType(TransactionalManagerType.of(backup, sbt.util.Logger.Null))
    val compiler = setup.createCompiler().copy(incOptions = options)
    try {
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile))

      compiler.doCompileWithStore(fileStore)

      // create fake Foo.tasty files
      val fooTastyFile = setup.classesDir.resolve("pkg/Foo.tasty")
      IO.touch(fooTastyFile.toFile)

      // remove Foo.scala
      val fooSrcFile = setup.baseDir.resolve(s"src/${SourceFiles.Foo}")
      IO.delete(fooSrcFile.toFile)

      // recompile
      val goodSrcFile = setup.baseDir.resolve(s"src/${SourceFiles.Good}")
      val newSources = Array(setup.converter.toVirtualFile(goodSrcFile))
      compiler.doCompileWithStore(
        fileStore,
        i => i.withOptions(i.options().withSources(newSources))
      )
      assert(!Files.exists(fooTastyFile))
    } finally compiler.close()
  }
}
