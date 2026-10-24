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
import HedgehogSupport.shuffle
import MiniSetupUtil.*

object MiniSetupUtilSpec extends Properties:
  override def tests: List[Test] = List(
    example("detect options change", detectChange),
    example("ignore options list different by specfied options (exact)", ignoreExact),
    example("use regex to ignore options", ignoreRegex),
    example("combine options with their parameters with space for analysis", combineParams),
    example("detect change for options with parameters", detectParamChange),
    example("ignore reordering of options", ignoreReordering),
    property("ignore any reordering of options with their parameters", propReordering),
    property("ignore options that match an ignored regex", propIgnoredOptions),
    property("detect a dropped option", propDroppedOption),
  )

  private def equiv(ignored: Array[String], before: Array[String], after: Array[String]) =
    equivScalacOptions(ignoredRegexes = ignored).equiv(before, after)

  private def detectChange: Result =
    equiv(Array(), Array("-deprecation"), Array()) ==== false

  private def ignoreExact: Result =
    equiv(
      Array("-Xprint:typer", "-Xfatal-warnings"),
      Array("-Xprint:typer", "-deprecation"),
      Array("-Xfatal-warnings", "-deprecation"),
    ) ==== true

  private def ignoreRegex: Result =
    equiv(
      Array("-Xprint:.*"),
      Array("-Xprint:typer"),
      Array("-Xprint:typer", "-Xprint:refcheck", "-Xprint:parse"),
    ) ==== true

  private def combineParams: Result =
    equiv(Array("-opt .*"), Array("-opt", "abc"), Array("-opt", "def")) ==== true

  private def detectParamChange: Result =
    equiv(Array("-opt a.*"), Array("-opt", "abc"), Array("-opt", "def")) ==== false

  private def ignoreReordering: Result =
    equiv(Array(), Array("-a", "1", "-b", "2", "-c"), Array("-b", "2", "-c", "-a", "1")) ==== true

  private val genName: Gen[String] = Gen.string(Gen.lower, Range.linear(1, 8))

  /** An option with an optional parameter, kept together so that shuffling cannot split them. */
  private val genGroup: Gen[List[String]] =
    for
      name <- genName
      param <- Gen.string(Gen.alphaNum, Range.linear(1, 8)).option
    yield s"-$name" :: param.toList

  private val genGroups: Gen[List[List[String]]] =
    Gen.list(genGroup, Range.linear(0, 10)).map(_.distinctBy(_.head))

  def propReordering: Property =
    for
      groups <- genGroups.forAll
      shuffled <- shuffle(groups).forAll
    yield equiv(Array(), groups.flatten.toArray, shuffled.flatten.toArray) ==== true

  def propIgnoredOptions: Property =
    for
      groups <- genGroups.forAll
      phases <- Gen.list(genName, Range.linear(1, 4)).forAll
      shuffled <- shuffle(groups ++ phases.map(p => List(s"-Xprint:$p"))).forAll
    yield equiv(Array("-Xprint:.*"), groups.flatten.toArray, shuffled.flatten.toArray) ==== true

  def propDroppedOption: Property =
    for
      groups <- genGroups.filter(_.nonEmpty).forAll
      i <- Gen.int(Range.linear(0, groups.size - 1)).forAll
    yield
      val dropped = groups.patch(i, Nil, 1)
      equiv(Array(), groups.flatten.toArray, dropped.flatten.toArray) ==== false
end MiniSetupUtilSpec
