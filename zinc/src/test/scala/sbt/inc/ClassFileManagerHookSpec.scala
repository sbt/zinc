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
import java.io.File
import sbt.io.IO
import xsbti.compile.ClassFileManager

object ClassFileManagerHookSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example("allow client to add their own class file manager", customClassFileManager),
  )

  private def customClassFileManager: Result = IO.withTemporaryDirectory { tempDir =>
    val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)
    var callbackCalled = 0
    var numDel = 0
    var numGen = 0
    val myClassFileManager = new ClassFileManager:
      @deprecated("legacy", "1.4.0")
      override def delete(classes: Array[File]): Unit =
        // delete is called twice, both paths root in `IncrementalCompilerImpl.compileInternal`:
        //   1. IncrementalCompilerImpl.prevAnalysis -> Incremental.prune
        //   2. IncrementalCommon.pruneClassFilesOfInvalidations
        callbackCalled += 1
        numDel += classes.length
      @deprecated("legacy", "1.4.0")
      override def generated(classes: Array[File]): Unit =
        callbackCalled += 1
        numGen += classes.length
      override def complete(success: Boolean): Unit =
        callbackCalled += 1
    val newExternalHooks =
      incOptions.externalHooks.withExternalClassFileManager(myClassFileManager)
    val options = incOptions.withExternalHooks(newExternalHooks)
    withCompiler(setup.createCompiler().copy(incOptions = options)) { compiler =>
      compiler.doCompile()
      Result.all(List(callbackCalled ==== 4, numDel ==== 0, numGen ==== 2))
    }
  }
end ClassFileManagerHookSpec
