package sbt.inc

import java.io.File

import sbt.io.IO

import xsbti.compile.ClassFileManager
import xsbti.compile.IncOptions

class ClassFileManagerHookSpec extends BaseCompilerSpec {
  it should "allow client to add their own class file manager" in {
    IO.withTemporaryDirectory { tempDir =>
      val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)

      var callbackCalled = 0
      val myClassFileManager = new ClassFileManager {
        override def delete(classes: Array[File]): Unit = {
          callbackCalled += 1
        }
        override def generated(classes: Array[File]): Unit = {
          callbackCalled += 1
        }
        override def complete(success: Boolean): Unit = {
          callbackCalled += 1
        }
      }

      val incOptions = IncOptions.of()
      val newExternalHooks =
        incOptions.externalHooks.withExternalClassFileManager(myClassFileManager)

      val compiler =
        setup.createCompiler().copy(incOptions = incOptions.withExternalHooks(newExternalHooks))
      compiler.doCompile()

      callbackCalled.shouldEqual(3)
    }
  }
}
