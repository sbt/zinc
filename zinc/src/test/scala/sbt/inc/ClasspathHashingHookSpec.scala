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
import java.util.Optional

import sbt.internal.inc.NoopExternalLookup
import sbt.io.IO
import xsbti.compile.DefaultExternalHooks
import xsbti.compile.FileHash
import xsbti.compile.IncOptions
import xsbti.VirtualFile

object ClasspathHashingHookSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example("allow client to override classpath hashing", overrideHashing),
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
          original ==== hashFile(setup.converter.toVirtualFile(original.file()))
        })
    }
  }
end ClasspathHashingHookSpec
