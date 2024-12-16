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

package sbt.inc.text

import org.scalacheck._
import sbt.inc.AnalysisFormatHelpers._
import sbt.inc.text.TextAnalysisFormatHelpers._
import sbt.internal.inc._
import sbt.internal.inc.text.TextAnalysisFormat

object TextAnalysisFormatSpecification extends Properties("TextAnalysisFormat") {
  property("round-trip empty") = forEmpty(check)
  property("round-trip simple") = forSimple(check)
  property("round-trip complex") = forComplex(check)

  private def check(analysis: Analysis) = {
    checkStoreRoundtrip(analysis, FileAnalysisStore.text(_)) &&
    checkTextRoundtrip(analysis, TextAnalysisFormat, TextAnalysisFormat)
  }
}
