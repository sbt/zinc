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

import java.io.{ BufferedReader, StringReader, StringWriter, Writer }

import sbt.inc.AnalysisFormatHelpers._
import sbt.internal.inc._
import sbt.internal.inc.text.TextAnalysisFormat

object TextAnalysisFormatHelpers {
  def checkTextRoundtrip(analysis: Analysis, wfmt: TextAnalysisFormat, rfmt: TextAnalysisFormat) = {
    val (readAnalysis, readSetup) = deserialize(serialize(analysis, wfmt), rfmt)
    compare(analysis, readAnalysis) && compare(commonSetup, readSetup)
  }

  private val companionStore = new CompanionsStore {
    def getUncaught() = (Map(), Map())
    def get() = Some(getUncaught())
  }

  private def serialize(analysis: Analysis, format: TextAnalysisFormat) =
    stringFromWriter(format.write(_, analysis, commonSetup))

  private def deserialize(from: String, format: TextAnalysisFormat) =
    format.read(new BufferedReader(new StringReader(from)), companionStore)

  private def stringFromWriter(write: Writer => Unit) = {
    val writer = new StringWriter()
    write(writer)
    writer.toString
  }
}
