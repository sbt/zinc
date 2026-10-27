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
import hedgehog.runner.*

object UsedNameSpec extends Properties:
  override def tests: List[Test] = List(
    example("escape a newline in the middle of a name", middle),
    example("escape a newline at the start of a name", leading),
    property("escape every newline of any name", propNoNewline),
  )

  private def middle: Result =
    UsedName("weird\nname").name ==== "weird♨0Aname"

  private def leading: Result =
    UsedName("\nname").name ==== "♨0Aname"

  def propNoNewline: Property =
    for
      name <- Gen
        .string(Gen.frequency1(4 -> Gen.alphaNum, 1 -> Gen.constant('\n')), Range.linear(0, 16))
        .forAll
    yield
      val used = UsedName(name)
      Result.all(
        List(
          Result.assert(!used.name.contains('\n')).log(s"stored: ${used.name}"),
          used.name ==== name.replace("\n", "♨0A"),
          AnalysisInterner.usedName(name, 1).name ==== used.name,
        )
      )
end UsedNameSpec
