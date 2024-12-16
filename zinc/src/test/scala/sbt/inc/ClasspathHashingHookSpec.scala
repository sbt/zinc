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

import java.util.Optional

import sbt.internal.inc.{ NoopExternalLookup }
import sbt.io.IO
import xsbti.compile.DefaultExternalHooks
import xsbti.compile.FileHash
import xsbti.compile.IncOptions
import xsbti.VirtualFile

class ClasspathHashingHookSpec extends BaseCompilerSpec {
  it should "allow client to override classpath hashing" in {
    IO.withTemporaryDirectory { tempDir =>
      val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)

      def hashFile(f: VirtualFile): FileHash = {
        FileHash.of(
          setup.converter.toPath(f),
          setup.converter.toPath(f).hashCode() * 37 + 41
        ) // Some deterministic random changes
      }
      val lookup = new NoopExternalLookup {
        override def hashClasspath(classpath: Array[VirtualFile]): Optional[Array[FileHash]] =
          Optional.of(classpath.map(hashFile))
      }
      val hooks = new DefaultExternalHooks(Optional.of(lookup), Optional.empty())
      val compiler =
        setup.createCompiler().copy(incOptions = IncOptions.of().withExternalHooks(hooks))
      try {
        val result = compiler.doCompile()

        result.setup().options().classpathHash().foreach { original =>
          original shouldEqual hashFile(setup.converter.toVirtualFile(original.file()))
        }
      } finally {
        compiler.close()
      }
    }
  }
}
