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

package sbt.inc.binary

import org.scalacheck.*
import sbt.inc.AnalysisFormatHelpers.*
import sbt.internal.inc.*

object BinaryAnalysisFormatSpecification extends Properties("BinaryAnalysisFormat"):
  property("round-trip empty") = forEmpty(check)
  property("round-trip simple") = forSimple(check)
  property("round-trip complex") = forComplex(check)

  private def check(analysis: Analysis) =
    checkStoreRoundtrip(analysis, FileAnalysisStore.binary(_))
