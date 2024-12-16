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

import java.io.File
import sbt.io.IO
import xsbti.compile.ClassFileManager

class ClassFileManagerHookSpec extends BaseCompilerSpec {
  it should "allow client to add their own class file manager" in {
    IO.withTemporaryDirectory { tempDir =>
      val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)

      var callbackCalled = 0
      var numDel = 0
      var numGen = 0
      val myClassFileManager = new ClassFileManager {
        @deprecated("legacy", "1.4.0")
        override def delete(classes: Array[File]): Unit = {
          // delete is called twice, both paths root in `IncrementalCompilerImpl.compileInternal`:
          //   1. IncrementalCompilerImpl.prevAnalysis -> Incremental.prune
          //   2. IncrementalCommon.pruneClassFilesOfInvalidations
          callbackCalled += 1
          numDel += classes.length
        }
        @deprecated("legacy", "1.4.0")
        override def generated(classes: Array[File]): Unit = {
          callbackCalled += 1
          numGen += classes.length
        }
        override def complete(success: Boolean): Unit = {
          callbackCalled += 1
        }
      }

      val newExternalHooks =
        incOptions.externalHooks.withExternalClassFileManager(myClassFileManager)
      val options = incOptions.withExternalHooks(newExternalHooks)

      val compiler = setup.createCompiler().copy(incOptions = options)
      try compiler.doCompile()
      finally compiler.close()

      callbackCalled.shouldEqual(4)
      numDel.shouldEqual(0)
      numGen.shouldEqual(2)
    }
  }
}
