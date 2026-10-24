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
import java.nio.file.{ Path, Paths }

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.{ Compilation, CompileOutput }
import sbt.internal.inc.text.CompilationFormat
import xsbti.compile.Output

object CompilationFormatSpec extends Properties:
  override def tests: List[Test] = List(
    example("single output round-trip", singleOutput),
    example("multiple output round-trip", multipleOutput),
    example("empty output round-trip", emptyOutput),
    property("any output round-trips", propRoundtrip),
  )

  private def roundtrip(c: Compilation): Compilation =
    val baos = new ByteArrayOutputStream
    CompilationFormat.writes(new sbinary.JavaOutput(baos), c)
    CompilationFormat.reads(new sbinary.JavaInput(new ByteArrayInputStream(baos.toByteArray)))

  private def singleOutput: Result =
    val r = roundtrip(Compilation(123L, CompileOutput(Paths.get("/tmp/classes"))))
    Result.all(
      List(
        r.getStartTime ==== 123L,
        r.getOutput.getSingleOutputAsPath.get ==== Paths.get("/tmp/classes"),
      )
    )

  private def multipleOutput: Result =
    val out = CompileOutput(
      Paths.get("/src/a") -> Paths.get("/out/a"),
      Paths.get("/src/b") -> Paths.get("/out/b")
    )
    val r = roundtrip(Compilation(456L, out))
    val groups = r.getOutput.getMultipleOutput.get
    Result.all(
      List(
        r.getStartTime ==== 456L,
        groups.length ==== 2,
        groups(0).getSourceDirectoryAsPath ==== Paths.get("/src/a"),
        groups(0).getOutputDirectoryAsPath ==== Paths.get("/out/a"),
        groups(1).getSourceDirectoryAsPath ==== Paths.get("/src/b"),
        groups(1).getOutputDirectoryAsPath ==== Paths.get("/out/b"),
      )
    )

  private def emptyOutput: Result =
    val r = roundtrip(Compilation(789L, CompileOutput.empty))
    Result.all(
      List(
        r.getStartTime ==== 789L,
        r.getOutput.getMultipleOutput.isPresent ==== false,
        r.getOutput.getSingleOutputAsPath.isPresent ==== false,
      )
    )

  private val genPath: Gen[Path] =
    Gen
      .list(Gen.string(Gen.alphaNum, Range.linear(1, 8)), Range.linear(1, 4))
      .map(segments => Paths.get(segments.mkString("/", "/", "")))

  /** An output and its comparable description: None, one path, or the (source, output) pairs. */
  private val genOutput: Gen[(Output, Either[Unit, Either[Path, List[(Path, Path)]]])] =
    Gen.choice1(
      Gen.constant((CompileOutput.empty, Left(()))),
      genPath.map(p => (CompileOutput(p), Right(Left(p)))),
      Gen
        .list(genPath.flatMap(s => genPath.map(o => s -> o)), Range.linear(1, 4))
        .map(groups => (CompileOutput(groups*), Right(Right(groups)))),
    )

  private def describe(output: Output): Either[Unit, Either[Path, List[(Path, Path)]]] =
    if output.getSingleOutputAsPath.isPresent then Right(Left(output.getSingleOutputAsPath.get))
    else if output.getMultipleOutput.isPresent then
      Right(Right(output.getMultipleOutput.get.toList.map(g =>
        g.getSourceDirectoryAsPath -> g.getOutputDirectoryAsPath
      )))
    else Left(())

  def propRoundtrip: Property =
    for
      startTime <- Gen.long(Range.linear(0L, Long.MaxValue)).forAll
      (output, expected) <- genOutput.forAll
    yield
      val r = roundtrip(Compilation(startTime, output))
      Result.all(List(r.getStartTime ==== startTime, describe(r.getOutput) ==== expected))
end CompilationFormatSpec
