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
import java.util.concurrent.atomic.AtomicLong
import sbt.internal.inc.LookupAnalysisFixture.{ Fixture, analysis, counting }

object LookupAnalysisSpec extends Properties:
  override def tests: List[Test] = List(
    property("lookupAnalysis agrees with the first match in classpath order", propFirstMatch),
    example("the first of two analyses that define a name wins, in either order", firstWins),
    example("warm lookups do not visit the analyses again", warmLookups),
  )

  private val names = List("p.A", "p.A$Inner", "p.M$")

  /** A classpath entry without an analysis, or with an analysis that defines some of `names`. */
  private val genEntry: Gen[Option[Analysis]] =
    for
      hasAnalysis <- Gen.boolean
      defines <- Gen.list(Gen.boolean, Range.singleton(names.size))
    yield Option.when(hasAnalysis)(analysis(names.zip(defines).collect { case (n, true) => n }))

  /** The position of a found analysis in `analyses`, so that a failure shows which one it is. */
  private def position(lookup: LookupImpl, found: Option[AnyRef]): Option[Int] =
    found.map(a => lookup.analyses.indexWhere(_ eq a))

  def propFirstMatch: Property =
    for entries <- Gen.list(genEntry, Range.linear(0, 6)).forAll
    yield
      val lookup = Fixture(entries.toVector).newLookup()
      val queries = names ++ List("p.Missing", "source.p.A")
      Result.all(queries.map { name =>
        val expected = lookup.analyses.find(_.relations.productClassName._2s.contains(name))
        (position(lookup, lookup.lookupAnalysis(name)) ==== position(lookup, expected))
          .log(s"name: $name")
      })

  private def firstWins: Result =
    val first = analysis(List("p.Dup"))
    val second = analysis(List("p.Dup"))
    Result.all(List(Vector(first, second), Vector(second, first)).map { order =>
      val lookup = Fixture(order.map(Some(_))).newLookup()
      position(lookup, lookup.lookupAnalysis("p.Dup")) ==== Some(0)
    })

  private def warmLookups: Result =
    val visits = new AtomicLong
    val entries = Vector.tabulate(20)(i => Some(counting(analysis(List(s"p$i.C")), visits)))
    val lookup = Fixture(entries).newLookup()
    val first = lookup.lookupAnalysis("p0.C")
    visits.set(0L)
    val hits = (0 until 20).map(i => lookup.lookupAnalysis(s"p$i.C"))
    val misses = (0 until 20).map(i => lookup.lookupAnalysis(s"absent$i.C"))
    Result.all(
      List(
        Result.assert(first.isDefined && hits.forall(_.isDefined)),
        Result.assert(misses.forall(_.isEmpty)),
        visits.get ==== 0L,
      )
    )
end LookupAnalysisSpec
