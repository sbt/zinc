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

import org.scalacheck.{ Gen, Prop, Properties }
import org.scalacheck.Prop.forAll

object UnorderedHasherSpecification extends Properties("UnorderedHasher") {

  /** Element hashes, some of them unordered hashes of their own children. */
  final case class Tree(hash: Int, children: List[Tree])

  private val genHash: Gen[Int] = Gen.oneOf(Gen.choose(Int.MinValue, Int.MaxValue), Gen.const(0))

  private def genTree(depth: Int): Gen[Tree] =
    for {
      h <- genHash
      n <- if (depth <= 0) Gen.const(0) else Gen.choose(0, 4)
      children <- Gen.listOfN(n, genTree(depth - 1))
    } yield Tree(h, children)

  /** What `HashAPI` would compute: each element mixes in the unordered hash of its children. */
  private def expected(t: Tree): Int =
    if (t.children.isEmpty) t.hash
    else MurmurHash3.mix(t.hash, MurmurHash3.unorderedHash(t.children.map(expected)))

  private def actual(hashers: UnorderedHashers, t: Tree): Int =
    if (t.children.isEmpty) t.hash
    else {
      val hasher = hashers.begin()
      t.children.foreach(c => hasher.add(actual(hashers, c)))
      MurmurHash3.mix(t.hash, hashers.end(hasher))
    }

  property("matches MurmurHash3.unorderedHash") = forAll(Gen.listOf(genHash)) { hs =>
    val hasher = new UnorderedHasher
    hs.foreach(hasher.add)
    hasher.result == MurmurHash3.unorderedHash(hs)
  }

  property("keeps nested hashes apart") = forAll(genTree(4)) { t =>
    actual(new UnorderedHashers, t) == expected(t)
  }

  property("reuses its hashers") = forAll(Gen.listOfN(3, genTree(4))) { ts =>
    val hashers = new UnorderedHashers
    Prop.all(ts.map(t => Prop(actual(hashers, t) == expected(t))): _*)
  }
}
