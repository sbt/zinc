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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*

object StableGroupsSpecification extends Properties:
  override def tests: List[Test] = List(
    property("groups like groupBy, in order of first appearance", propGroupBy),
    property("lays the groups out back to back", propBackToBack),
    example("empty", empty),
  )

  final case class Item(key: String, id: Int)

  // "Aa" and "BB" share a hashCode, as do all their concatenations of the same length.
  private val collidingKeys = List("Aa", "BB", "AaAa", "AaBB", "BBAa", "BBBB")

  private val genIdentifier: Gen[String] =
    for
      c <- Gen.lower
      cs <- Gen.string(Gen.alphaNum, Range.linear(0, 10))
    yield s"$c$cs"

  private val genKey: Gen[String] = Gen.frequency1(
    3 -> Gen.elementUnsafe(collidingKeys),
    3 -> Gen.element1("a", "b", "c", ""),
    1 -> genIdentifier,
  )

  private val genItems: Gen[Array[Item]] =
    Gen
      .sized(n => Gen.list(genKey, Range.singleton(n.value * 4)))
      .map(_.iterator.zipWithIndex.map(Item(_, _)).toArray)

  private def groupsOf(items: Array[Item]): List[(String, List[Item])] =
    val groups = StableGroups(items, (_: Item).key)
    List.tabulate(groups.size) { g =>
      groups.key(g) -> groups.elements.slice(groups.from(g), groups.until(g)).toList
    }

  def propGroupBy: Property =
    for items <- genItems.forAll
    yield
      val expected =
        items.map(_.key).distinct.toList.map(k => k -> items.filter(_.key == k).toList)
      groupsOf(items) ==== expected

  def propBackToBack: Property =
    for items <- genItems.forAll
    yield
      val groups = StableGroups(items, (_: Item).key)
      val bounds = List.tabulate(groups.size)(g => (groups.from(g), groups.until(g)))
      Result
        .all(
          List(
            groups.elements.length ==== items.length,
            Result.assert(bounds.headOption.forall(_._1 == 0)),
            Result.assert(bounds.lastOption.forall(_._2 == items.length)),
            Result.assert(bounds.zip(bounds.drop(1)).forall { case ((_, until), (from, _)) =>
              until == from
            }),
            Result.assert(bounds.forall { case (from, until) => from < until }),
          )
        )
        .log(s"bounds: $bounds")

  private def empty: Result = groupsOf(Array.empty[Item]) ==== Nil
end StableGroupsSpecification
