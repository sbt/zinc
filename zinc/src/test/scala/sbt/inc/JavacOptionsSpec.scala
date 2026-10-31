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
import sbt.io.IO
import sbt.io.syntax.*
import xsbti.compile.Inputs

object JavacOptionsSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example("resolve virtual paths in javacOptions (sbt/sbt#10029)", virtualPaths),
  )

  private val source =
    """package pkg;
      |public class Native {
      |  public static native int answer();
      |}
      |""".stripMargin

  private def virtualPaths: Result = IO.withTemporaryDirectory: tmp =>
    val proj = VirtualSubproject(tmp.toPath / "p")
    val src = proj.baseDir / "src" / "pkg" / "Native.java"
    IO.write(src.toFile, source)
    withCompiler(proj.setup.createCompiler()): compiler =>
      val withHeaders = (in: Inputs) =>
        in.withOptions(
          in.options
            .withSources(Array(proj.converter.toVirtualFile(src)))
            .withJavacOptions(Array("-h", "${BASE}/headers"))
        )
      compiler.doCompile(withHeaders)
      compiler.doCompileAllJava(withHeaders)
      Result.all(
        List(
          assertExists(proj.baseDir / "headers" / "pkg_Native.h"),
          assertNotExists(proj.baseDir / "${BASE}"),
        )
      )
end JavacOptionsSpec
