/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc.text

import java.nio.file.Paths

import org.scalacheck.{ Prop, Properties }
import sbt.internal.inc._
import sbt.internal.inc.text.TextAnalysisFormat
import xsbti.compile.analysis.ReadWriteMappers

import scala.util.Try

object TextMappersSpecification extends Properties("TextMappers") with BaseTextAnalysisFormatTest {

  override def RootFilePath = "/tmp/localProject"
  private final val mappers: ReadWriteMappers =
    ReadWriteMappers.getMachineIndependentMappers(Paths.get(RootFilePath))

  override val analysisGenerators: AnalysisGenerators = new AnalysisGenerators {
    override def RootFilePath = TextMappersSpecification.RootFilePath
  }

  override def format: TextAnalysisFormat = new TextAnalysisFormat(mappers)
  override protected def checkAnalysis(analysis: Analysis): Prop = {
    def checkFormatFail(readFormat: TextAnalysisFormat, writeFormat: TextAnalysisFormat): Prop = {
      val analisisMatch: Boolean = Try {
        val (newAnalysis, newSetup) = deserialize(serialize(analysis, writeFormat), readFormat)
        newAnalysis != analysis && newSetup != commonSetup
      }.getOrElse(false)
      analisisMatch == false
    }

    //("READ mapped by standard" |: checkFormatFail(TextAnalysisFormat, format)) &&
    ("READ standard by mapped" |: checkFormatFail(format, TextAnalysisFormat)) &&
    super.checkAnalysis(analysis)
  }
}
