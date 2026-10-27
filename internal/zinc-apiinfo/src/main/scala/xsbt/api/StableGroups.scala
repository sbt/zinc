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

/**
 * The elements of an array grouped by a key, as `groupBy` would, but with the groups in the order
 * their keys first appear and each group's elements in their original order.
 *
 * Group `g` holds `elements(from(g) until until(g))`. Grouping allocates a handful of arrays, and
 * nothing per element: keys are compared with `equals` after `hashCode` in an open-addressed table.
 */
private[api] final class StableGroups[A <: AnyRef, K <: AnyRef] private (
    val elements: Array[A],
    keys: Array[AnyRef],
    starts: Array[Int],
    val size: Int
):
  def key(g: Int): K = keys(g).asInstanceOf[K]
  def from(g: Int): Int = starts(g)
  def until(g: Int): Int = starts(g + 1)

private[api] object StableGroups:
  def apply[A <: AnyRef, K <: AnyRef](xs: Array[A], keyOf: A => K): StableGroups[A, K] =
    val n = xs.length
    val groupOf = new Array[Int](n)
    val keys = new Array[AnyRef](n)
    val counts = new Array[Int](n)
    var groups = 0
    // From a key's slot to 1 + its group; 0 is empty. At most a quarter full.
    val table = new Array[Int](Integer.highestOneBit(math.max(n, 1)) * 4)
    val mask = table.length - 1
    var i = 0
    while i < n do
      val k = keyOf(xs(i))
      var slot = scala.util.hashing.byteswap32(k.hashCode) & mask
      var g = -1
      while g < 0 do
        val entry = table(slot)
        if entry == 0 then
          g = groups
          groups += 1
          keys(g) = k
          table(slot) = g + 1
        else if keys(entry - 1) == k then g = entry - 1
        else slot = (slot + 1) & mask
      groupOf(i) = g
      counts(g) += 1
      i += 1
    end while
    val starts = new Array[Int](groups + 1)
    var g = 0
    while g < groups do
      starts(g + 1) = starts(g) + counts(g)
      counts(g) = 0
      g += 1
    val elements = xs.clone()
    i = 0
    while i < n do
      val g = groupOf(i)
      elements(starts(g) + counts(g)) = xs(i)
      counts(g) += 1
      i += 1
    new StableGroups(elements, keys, starts, groups)
  end apply
end StableGroups
