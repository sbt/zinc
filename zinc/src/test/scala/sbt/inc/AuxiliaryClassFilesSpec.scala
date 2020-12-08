package sbt.inc

import sbt.io.IO
import xsbti.compile.AuxiliaryClassFiles

import java.nio.file.Path

class AuxiliaryClassFilesSpec extends BaseCompilerSpec {
  it should "allow client to add define their own auxiliary class files" in {
    IO.withTemporaryDirectory { tempDir =>
      val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)
      var callbackCalled = 0
      val myAuxiliaryFiles = new AuxiliaryClassFiles {
        override def associatedFiles(classFile: Path): Array[Path] = {
          callbackCalled += 1
          Array()
        }
      }

      val compiler =
        setup.createCompiler(incOptions = incOptions.withAuxiliaryClassFiles(Array(myAuxiliaryFiles)))

      try compiler.doCompile()
      finally compiler.close()

      callbackCalled.shouldEqual(3)
    }
  }
}
