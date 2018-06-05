package sbt.inc

import java.io.File
import java.util.Optional

import sbt.internal.inc.NoopExternalLookup
import sbt.io.IO
import xsbti.compile.DefaultExternalHooks
import xsbti.compile.FileHash
import xsbti.compile.IncOptions

class ClasspathHashingHookSpec extends BaseCompilerSpec {
  it should "allow client to override classpath hashing" in {
    IO.withTemporaryDirectory { tempDir =>
      val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)

      def hashFile(f: File): FileHash =
        FileHash.of(f, f.hashCode() * 37 + 41) // Some deterministic random changes

      val lookup = new NoopExternalLookup {
        override def hashClasspath(classpath: Array[File]): Optional[Array[FileHash]] =
          Optional.of(classpath.map(hashFile))

      }
      val hooks = new DefaultExternalHooks(Optional.of(lookup), Optional.empty())
      val compiler =
        setup.createCompiler().copy(incOptions = IncOptions.of().withExternalHooks(hooks))
      val result = compiler.doCompile()

      result.setup().options().classpathHash().foreach { original =>
        original shouldEqual hashFile(original.file())
      }
    }
  }
}
