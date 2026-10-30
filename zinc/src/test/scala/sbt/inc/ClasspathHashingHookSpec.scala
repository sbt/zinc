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
import java.nio.file.Paths
import java.util.Optional

import sbt.internal.inc.NoopExternalLookup
import sbt.io.IO
import xsbti.compile.DefaultExternalHooks
import xsbti.compile.FileHash
import xsbti.compile.IncOptions
import xsbti.VirtualFile
import xsbti.VirtualFileRef

object ClasspathHashingHookSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example("allow client to override classpath hashing", overrideHashing),
    example("record classpath hash entries by their virtual id", virtualIds),
  )

  private def overrideHashing: Result = IO.withTemporaryDirectory { tempDir =>
    val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)

    def hashFile(f: VirtualFile): FileHash =
      FileHash.of(
        setup.converter.toPath(f),
        setup.converter.toPath(f).hashCode() * 37 + 41
      ) // Some deterministic random changes

    val lookup = new NoopExternalLookup:
      override def hashClasspath(classpath: Array[VirtualFile]): Optional[Array[FileHash]] =
        Optional.of(classpath.map(hashFile))
    val hooks = new DefaultExternalHooks(Optional.of(lookup), Optional.empty())
    withCompiler(setup.createCompiler().copy(incOptions =
      IncOptions.of().withExternalHooks(hooks)
    )) {
      compiler =>
        val result = compiler.doCompile()
        Result.all(result.setup().options().classpathHash().toList.map { original =>
          val vf = setup.converter.toVirtualFile(
            setup.converter.toPath(VirtualFileRef.of(original.file().toString))
          )
          original.hash() ==== hashFile(vf).hash()
        })
    }
  }

  private def virtualIds: Result = IO.withTemporaryDirectory { tempDir =>
    val setup = ProjectSetup.simple(tempDir.toPath, SourceFiles.Foo :: Nil)
    withCompiler(setup.createCompiler()) { compiler =>
      val files = compiler.doCompile().setup().options().classpathHash().toList.map(_.file())
      val base = setup.baseDir.toString
      Result.all(
        Result.assert(files.exists(_.startsWith(Paths.get("${BASE}")))) ::
          files.map(f => Result.assert(!f.toString.startsWith(base)).log(f.toString))
      )
    }
  }
end ClasspathHashingHookSpec
