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

package sbt
package internal
package inc

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*

object HashSpec extends Properties:
  override def tests: List[Test] = List(
    example("reject goddleygook", reject("goddleygook")),
    example("accept lower hex", accept("0123456789abcdef")),
    example("accept upper hex", accept("0123456789ABCDEF")),
    example("reject odd number", reject("012345678")),
    example("reject an empty hash", reject("")),
    property("accept any non-empty even-length hex string", propAcceptEvenHex),
    property("reject any odd-length hex string", propRejectOddHex),
    property("reject a string with a non-hex character", propRejectNonHex),
  )

  private def reject(s: String): Result = (run(s) ==== None).log(s"input: $s")
  private def accept(s: String): Result = (run(s).map(_.hexHash) ==== Some(s)).log(s"input: $s")
  private def run(s: String): Option[Hash] = Hash.fromString(s"hash($s)")

  private val genHexit: Gen[Char] = Gen.choice1(Gen.hexit, Gen.char('A', 'F'))

  private def genHex(pairs: Range[Int]): Gen[String] =
    Gen.list(Gen.string(genHexit, Range.singleton(2)), pairs).map(_.mkString)

  def propAcceptEvenHex: Property =
    for s <- genHex(Range.linear(1, 32)).forAll
    yield accept(s)

  def propRejectOddHex: Property =
    for
      s <- genHex(Range.linear(0, 32)).forAll
      c <- genHexit.forAll
    yield reject(s + c)

  def propRejectNonHex: Property =
    for
      s <- genHex(Range.linear(1, 32)).forAll
      i <- Gen.int(Range.linear(0, 63)).forAll
      c <- Gen.alpha.filter(c => !"abcdefABCDEF".contains(c)).forAll
    yield
      val at = i % s.length
      reject(s.updated(at, c))
end HashSpec
