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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.inc.AnalysisFormatHelpers.*
import sbt.internal.inc.*

object BinaryAnalysisFormatSpecification extends Properties:
  override def tests: List[Test] = roundTrips(check)

  private def check(analysis: Analysis) =
    checkStoreRoundtrip(analysis, FileAnalysisStore.binary(_))
