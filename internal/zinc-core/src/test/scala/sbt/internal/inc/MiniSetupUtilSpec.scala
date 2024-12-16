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

import MiniSetupUtil._

class MiniSetupUtilSpec extends UnitSpec {

  it should "detect options change" in {
    val equiv = equivScalacOptions(ignoredRegexes = Array())

    val before = Array("-deprecation")
    val after = Array[String]()

    equiv.equiv(before, after) shouldBe false
  }

  it should "ignore options list different by specfied options (exact)" in {
    val equiv = equivScalacOptions(ignoredRegexes = Array("-Xprint:typer", "-Xfatal-warnings"))

    val before = Array("-Xprint:typer", "-deprecation")
    val after = Array("-Xfatal-warnings", "-deprecation")

    equiv.equiv(before, after) shouldBe true
  }

  it should "use regex to ignore options" in {
    val equiv = equivScalacOptions(ignoredRegexes = Array("-Xprint:.*"))

    val before = Array("-Xprint:typer")
    val after = Array("-Xprint:typer", "-Xprint:refcheck", "-Xprint:parse")

    equiv.equiv(before, after) shouldBe true
  }

  it should "combine options with their parameters with space for analysis" in {
    val equiv = equivScalacOptions(ignoredRegexes = Array("-opt .*"))

    val before = Array("-opt", "abc")
    val after = Array("-opt", "def")

    equiv.equiv(before, after) shouldBe true
  }

  it should "detect change for options with parameters" in {
    val equiv = equivScalacOptions(ignoredRegexes = Array("-opt a.*"))

    val before = Array("-opt", "abc")
    val after = Array("-opt", "def")

    equiv.equiv(before, after) shouldBe false
  }

  it should "ignore reordering of options" in {
    val equiv = equivScalacOptions(ignoredRegexes = Array())

    val before = Array("-a", "1", "-b", "2", "-c")
    val after = Array("-b", "2", "-c", "-a", "1")

    equiv.equiv(before, after) shouldBe true
  }

}
