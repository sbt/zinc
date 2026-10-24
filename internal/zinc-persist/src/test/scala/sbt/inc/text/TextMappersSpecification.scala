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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.inc.AnalysisFormatHelpers.*
import sbt.inc.text.TextAnalysisFormatHelpers.*
import sbt.internal.inc.{ mappers as _, * }
import sbt.internal.inc.AnalysisGenerators.*
import sbt.internal.inc.text.TextAnalysisFormat

object TextMappersSpecification extends Properties:
  override def tests: List[Test] = roundTrips(check)

  private val mappedFormat = new TextAnalysisFormat(mappers)

  private def check(analysis: Analysis) =
    Result.all(
      List(
        checkStoreRoundtrip(analysis, FileAnalysisStore.text(_, mappedFormat)),
        checkTextRoundtrip(analysis, mappedFormat, mappedFormat),
        checkTextRoundtrip(analysis, mappedFormat, TextAnalysisFormat),
        throws(checkTextRoundtrip(analysis, TextAnalysisFormat, mappedFormat)),
      )
    )
