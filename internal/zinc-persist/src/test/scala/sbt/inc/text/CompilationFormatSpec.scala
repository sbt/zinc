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

package sbt.inc.text

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import java.nio.file.Paths

import org.scalatest.funsuite.AnyFunSuite
import sbt.internal.inc.{ Compilation, CompileOutput }
import sbt.internal.inc.text.CompilationFormat

class CompilationFormatSpec extends AnyFunSuite {

  private def roundtrip(c: Compilation): Compilation = {
    val baos = new ByteArrayOutputStream
    CompilationFormat.writes(new sbinary.JavaOutput(baos), c)
    CompilationFormat.reads(new sbinary.JavaInput(new ByteArrayInputStream(baos.toByteArray)))
  }

  test("single output round-trip") {
    val r = roundtrip(Compilation(123L, CompileOutput(Paths.get("/tmp/classes"))))
    assert(r.getStartTime == 123L)
    assert(r.getOutput.getSingleOutputAsPath.get == Paths.get("/tmp/classes"))
  }

  test("multiple output round-trip") {
    val out = CompileOutput(
      Paths.get("/src/a") -> Paths.get("/out/a"),
      Paths.get("/src/b") -> Paths.get("/out/b")
    )
    val r = roundtrip(Compilation(456L, out))
    val groups = r.getOutput.getMultipleOutput.get
    assert(r.getStartTime == 456L)
    assert(groups.length == 2)
    assert(groups(0).getSourceDirectoryAsPath == Paths.get("/src/a"))
    assert(groups(0).getOutputDirectoryAsPath == Paths.get("/out/a"))
    assert(groups(1).getSourceDirectoryAsPath == Paths.get("/src/b"))
    assert(groups(1).getOutputDirectoryAsPath == Paths.get("/out/b"))
  }

  test("empty output round-trip") {
    val r = roundtrip(Compilation(789L, CompileOutput.empty))
    assert(r.getStartTime == 789L)
    assert(!r.getOutput.getMultipleOutput.isPresent)
    assert(!r.getOutput.getSingleOutputAsPath.isPresent)
  }
}
