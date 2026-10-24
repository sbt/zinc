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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.text.*

object Base64Specification extends Properties:
  override def tests: List[Test] = List(
    property("java89", propJava89),
  )

  val java89Encoder = new Java89Encoder

  def propJava89: Property =
    for bs <- Gen.bytes(Range.linear(0, 100)).forAll
    yield java89Encoder.decode(java89Encoder.encode(bs)).toList ==== bs.toList
