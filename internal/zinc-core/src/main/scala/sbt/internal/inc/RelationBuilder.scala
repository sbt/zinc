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

import sbt.internal.util.Relation

import scala.collection.{ immutable, mutable }

final class RelationBuilder[A, B]() {
  private[this] val forward =
    new java.util.HashMap[A, (A, mutable.Builder[B, immutable.HashSet[B]])]()
  private[this] val reverse =
    new java.util.HashMap[B, (B, mutable.Builder[A, immutable.HashSet[A]])]()

  def update(a: A, b: B): Unit = {
    val (internedB, asBuilder) =
      reverse.computeIfAbsent(b, (b => (b, immutable.HashSet.newBuilder[A])))
    val (internedA, bsBuilder) =
      forward.computeIfAbsent(a, (a => (a, immutable.HashSet.newBuilder[B])))
    asBuilder += internedA
    bsBuilder += internedB
  }

  def +=(other: Relation[A, B]): Unit = {
    for ((a, bs) <- other.forwardMap.iterator) {
      for (b <- bs) {
        update(a, b)
      }
    }
  }

  def result(): Relation[A, B] = {
    def toImmutable[K, V](
        map: java.util.HashMap[K, (K, mutable.Builder[V, immutable.HashSet[V]])]
    ): immutable.HashMap[K, immutable.HashSet[V]] = {
      val builder = immutable.HashMap.newBuilder[K, immutable.HashSet[V]]
      map.entrySet().forEach(e => builder.+=((e.getKey, e.getValue._2.result())))
      builder.result()
    }
    Relation.make[A, B](toImmutable(forward), toImmutable(reverse))
  }
}
