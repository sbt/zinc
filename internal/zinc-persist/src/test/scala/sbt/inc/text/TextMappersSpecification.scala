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

import org.scalacheck.*
import sbt.inc.AnalysisFormatHelpers.*
import sbt.inc.text.TextAnalysisFormatHelpers.*
import sbt.internal.inc.{ mappers as _, * }
import sbt.internal.inc.AnalysisGenerators.*
import sbt.internal.inc.text.TextAnalysisFormat

object TextMappersSpecification extends Properties("TextMappers"):
  property("round-trip empty") = forEmpty(check)
  property("round-trip simple") = forSimple(check)
  property("round-trip complex") = forComplex(check)

  private val mappedFormat = new TextAnalysisFormat(mappers)

  private def check(analysis: Analysis) =
    checkStoreRoundtrip(analysis, FileAnalysisStore.text(_, mappedFormat))
    checkTextRoundtrip(analysis, mappedFormat, mappedFormat) &&
    checkTextRoundtrip(analysis, mappedFormat, TextAnalysisFormat) &&
    throws(checkTextRoundtrip(analysis, TextAnalysisFormat, mappedFormat))
