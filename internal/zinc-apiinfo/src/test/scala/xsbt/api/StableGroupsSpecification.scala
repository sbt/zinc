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

import org.scalacheck.{ Gen, Prop, Properties }
import org.scalacheck.Prop.forAll

object StableGroupsSpecification extends Properties("StableGroups") {
  final case class Item(key: String, id: Int)

  // "Aa" and "BB" share a hashCode, as do all their concatenations of the same length.
  private val collidingKeys = List("Aa", "BB", "AaAa", "AaBB", "BBAa", "BBBB")

  private val genKey: Gen[String] = Gen.frequency(
    3 -> Gen.oneOf(collidingKeys),
    3 -> Gen.oneOf("a", "b", "c", ""),
    1 -> Gen.identifier
  )

  private val genItems: Gen[Array[Item]] =
    Gen
      .sized(n => Gen.listOfN(n * 4, genKey))
      .map(_.iterator.zipWithIndex.map { case (k, i) => Item(k, i) }.toArray)

  private def groupsOf(items: Array[Item]): List[(String, List[Item])] = {
    val groups = StableGroups(items, (_: Item).key)
    List.tabulate(groups.size) { g =>
      groups.key(g) -> groups.elements.slice(groups.from(g), groups.until(g)).toList
    }
  }

  property("groups like groupBy, in order of first appearance") = forAll(genItems) { items =>
    val expected = items.map(_.key).distinct.toList.map(k => k -> items.filter(_.key == k).toList)
    groupsOf(items) == expected
  }

  property("lays the groups out back to back") = forAll(genItems) { items =>
    val groups = StableGroups(items, (_: Item).key)
    val bounds = List.tabulate(groups.size)(g => (groups.from(g), groups.until(g)))
    Prop.all(
      Prop(groups.elements.length == items.length),
      Prop(bounds.headOption.forall(_._1 == 0)),
      Prop(bounds.lastOption.forall(_._2 == items.length)),
      Prop(bounds.zip(bounds.drop(1)).forall { case ((_, until), (from, _)) => until == from }),
      Prop(bounds.forall { case (from, until) => from < until })
    )
  }

  property("empty") = Prop(groupsOf(Array.empty[Item]).isEmpty)
}
