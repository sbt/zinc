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

import java.io.File
import sbt.io.IO
import xsbti.compile.ClassFileManager

class ClassFileManagerHookSpec extends BaseCompilerSpec {
  it should "allow client to add their own class file manager" in {
    IO.withTemporaryDirectory { tempDir =>
      val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)

      var callbackCalled = 0
      val myClassFileManager = new ClassFileManager {
        @deprecated("legacy", "1.4.0")
        override def delete(classes: Array[File]): Unit = {
          callbackCalled += 1
        }
        @deprecated("legacy", "1.4.0")
        override def generated(classes: Array[File]): Unit = {
          callbackCalled += 1
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

      callbackCalled.shouldEqual(3)
    }
  }
}
