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

package sbt.internal.inc

import hedgehog.{ sbt as _, * }
import hedgehog.core.SourcePos
import java.nio.file.Path
import sbt.io.IO
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/** Assertions and generators shared by the HedgeHog test suites. */
object HedgehogSupport:
  def assertThrows[E <: Throwable: ClassTag](body: => Any)(using SourcePos): Result =
    intercept[E](body).fold(identity, _ => Result.success)

  def intercept[E <: Throwable](body: => Any)(using
      ct: ClassTag[E],
      pos: SourcePos
  ): Either[Result, E] =
    val expected = ct.runtimeClass.getName
    try
      body
      Left(Result.failure.log(s"expected $expected to be thrown"))
    catch
      case ct(e)       => Right(e)
      case NonFatal(e) => Left(Result.failure.log(s"expected $expected, but got $e"))

  def contains(actual: String, expected: String)(using SourcePos): Result =
    Result.diffNamed("=== Not Contained ===", actual, expected)(_.contains(_))

  def notContains(actual: String, unexpected: String)(using SourcePos): Result =
    Result.diffNamed("=== Unexpectedly Contained ===", actual, unexpected)(!_.contains(_))

  /** Succeeds while `body` fails, so a fix shows up as a failure that asks to enable the test. */
  def pendingUntilFixed(body: => Result)(using SourcePos): Result =
    val passes =
      try body.success
      catch case NonFatal(_) => false
    if passes then Result.failure.log("the pending test passes now; make it a regular test")
    else Result.success

  def shuffle[A](xs: List[A]): Gen[List[A]] =
    Gen
      .list(Gen.int(Range.constant(0, Int.MaxValue)), Range.singleton(xs.size))
      .map(keys => xs.zip(keys).sortBy(_._2).map(_._1))

  def withTempDir(f: Path => Result): Result =
    IO.withTemporaryDirectory(dir => f(dir.toPath))
end HedgehogSupport
