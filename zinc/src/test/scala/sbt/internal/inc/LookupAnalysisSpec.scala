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
import xsbti.VirtualFileRef

object LookupAnalysisSpec extends Properties:
  override def tests: List[Test] = List(
    property("the index agrees with the first match in classpath order", propFirstMatch),
    example("the first of two analyses that define a name wins, in either order", firstWins),
    example("a defined name is found and an undefined one is not", hitAndMiss),
  )

  private val names = List("p.A", "p.A$Inner", "p.M$")

  /** An analysis whose classes have these binary names. */
  private def analysis(binaryNames: Seq[String]): Analysis =
    val classes = binaryNames.map(name => s"source.$name" -> name)
    Analysis.empty.copy(relations = Relations.empty.addClasses(VirtualFileRef.of("src"), classes))

  /** An analysis that defines some of `names`. */
  private val genAnalysis: Gen[Analysis] =
    Gen
      .list(Gen.boolean, Range.singleton(names.size))
      .map(defines => analysis(names.zip(defines).collect { case (n, true) => n }))

  /** The position of a found analysis in `analyses`, so that a failure shows which one it is. */
  private def position(analyses: Seq[Analysis], found: Option[Analysis]): Option[Int] =
    found.map(a => analyses.indexWhere(_ eq a))

  def propFirstMatch: Property =
    for analyses <- Gen.list(genAnalysis, Range.linear(0, 6)).forAll
    yield
      val index = LookupImpl.indexByBinaryClassName(analyses)
      Result.all((names ++ List("p.Missing", "source.p.A")).map: name =>
        val expected = analyses.find(_.relations.productClassName._2s.contains(name))
        (position(analyses, Option(index.get(name))) ==== position(analyses, expected))
          .log(s"name: $name"))

  private def firstWins: Result =
    val first = analysis(List("p.Dup"))
    val second = analysis(List("p.Dup"))
    Result.all(List(List(first, second), List(second, first)).map: order =>
      position(order, Option(LookupImpl.indexByBinaryClassName(order).get("p.Dup"))) ==== Some(0))

  private def hitAndMiss: Result =
    val analyses = List(analysis(List("p.A")), analysis(List("q.B", "q.B$")))
    val index = LookupImpl.indexByBinaryClassName(analyses)
    Result.all(
      List(
        position(analyses, Option(index.get("p.A"))) ==== Some(0),
        position(analyses, Option(index.get("q.B$"))) ==== Some(1),
        position(analyses, Option(index.get("q.C"))) ==== None,
        position(analyses, Option(index.get("source.p.A"))) ==== None,
      )
    )
end LookupAnalysisSpec
