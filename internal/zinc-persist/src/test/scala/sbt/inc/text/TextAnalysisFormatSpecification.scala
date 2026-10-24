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
import sbt.internal.inc.*
import sbt.internal.inc.text.TextAnalysisFormat

object TextAnalysisFormatSpecification extends Properties:
  override def tests: List[Test] = roundTrips(check)

  private def check(analysis: Analysis) =
    checkStoreRoundtrip(analysis, FileAnalysisStore.text(_)).and(
      checkTextRoundtrip(analysis, TextAnalysisFormat, TextAnalysisFormat)
    )
