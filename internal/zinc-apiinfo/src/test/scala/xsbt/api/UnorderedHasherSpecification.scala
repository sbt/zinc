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

package xsbt.api

import scala.util.hashing.MurmurHash3

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*

object UnorderedHasherSpecification extends Properties:
  override def tests: List[Test] = List(
    property("matches MurmurHash3.unorderedHash", propMatches),
    property("keeps nested hashes apart", propNested),
    property("reuses its hashers", propReuse),
  )

  /** Element hashes, some of them unordered hashes of their own children. */
  final case class Tree(hash: Int, children: List[Tree])

  private val genHash: Gen[Int] =
    Gen.choice1(Gen.int(Range.linearFrom(0, Int.MinValue, Int.MaxValue)), Gen.constant(0))

  private def genTree(depth: Int): Gen[Tree] =
    for
      h <- genHash
      n <- if depth <= 0 then Gen.constant(0) else Gen.int(Range.linear(0, 4))
      children <- Gen.list(genTree(depth - 1), Range.singleton(n))
    yield Tree(h, children)

  /** What `HashAPI` would compute: each element mixes in the unordered hash of its children. */
  private def expected(t: Tree): Int =
    if t.children.isEmpty then t.hash
    else MurmurHash3.mix(t.hash, MurmurHash3.unorderedHash(t.children.map(expected)))

  private def actual(hashers: UnorderedHashers, t: Tree): Int =
    if t.children.isEmpty then t.hash
    else
      val hasher = hashers.begin()
      t.children.foreach(c => hasher.add(actual(hashers, c)))
      MurmurHash3.mix(t.hash, hashers.end(hasher))

  def propMatches: Property =
    for hs <- Gen.list(genHash, Range.linear(0, 100)).forAll
    yield
      val hasher = new UnorderedHasher
      hs.foreach(hasher.add)
      hasher.result ==== MurmurHash3.unorderedHash(hs)

  def propNested: Property =
    for t <- genTree(4).forAll
    yield actual(new UnorderedHashers, t) ==== expected(t)

  def propReuse: Property =
    for ts <- Gen.list(genTree(4), Range.singleton(3)).forAll
    yield
      val hashers = new UnorderedHashers
      Result.all(ts.map(t => actual(hashers, t) ==== expected(t)))
end UnorderedHasherSpecification
