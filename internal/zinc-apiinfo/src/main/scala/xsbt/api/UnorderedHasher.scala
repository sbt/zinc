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

/**
 * `MurmurHash3.unorderedHash` of element hashes added one at a time, without boxing them.
 *
 * How it folds a hash into the product differs between Scala 2.12 and 2.13, so that step follows
 * the library this is built against; see `UnorderedHashCompat`.
 */
private[api] final class UnorderedHasher {
  private[this] var sum, xor, count = 0
  private[this] var product = 1

  /** The enclosing hash, for a caller to resume once this one is done. */
  var outer: Int = 0

  def reset(): Unit = {
    sum = 0
    xor = 0
    product = 1
    count = 0
  }

  def add(h: Int): Unit = {
    sum += h
    xor ^= h
    product = UnorderedHashCompat.product(product, h)
    count += 1
  }

  def result: Int = {
    var h = MurmurHash3.traversableSeed
    h = MurmurHash3.mix(h, sum)
    h = MurmurHash3.mix(h, xor)
    h = MurmurHash3.mixLast(h, product)
    MurmurHash3.finalizeHash(h, count)
  }
}

/**
 * An [[UnorderedHasher]] for each level of nested unordered hashes, as hashing one element can
 * start another. Each is allocated the first time its level is reached, and reused after.
 */
private[api] final class UnorderedHashers {
  private[this] var hashers = new Array[UnorderedHasher](4)
  private[this] var depth = -1

  def begin(): UnorderedHasher = {
    depth += 1
    if (depth == hashers.length) hashers = java.util.Arrays.copyOf(hashers, depth * 2)
    var hasher = hashers(depth)
    if (hasher == null) {
      hasher = new UnorderedHasher
      hashers(depth) = hasher
    } else hasher.reset()
    hasher
  }

  /** The result of `hasher`, the innermost one begun and not yet ended. */
  def end(hasher: UnorderedHasher): Int = {
    depth -= 1
    hasher.result
  }
}
