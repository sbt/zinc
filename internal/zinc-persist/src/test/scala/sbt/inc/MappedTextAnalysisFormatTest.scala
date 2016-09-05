/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc

import java.io.File

import org.scalacheck.{ Prop, Properties }
import sbt.internal.inc._

import scala.util.{ Random, Try }

object MappedTextAnalysisFormatTest extends Properties("MappedTextAnalysisFormat") with BaseTextAnalysisFormatTest {
  object TestMapper extends AnalysisMappers {
    override val sourceMapper: Mapper[File] = mapped(Mapper.forFile)
    override val productMapper: Mapper[File] = mapped(Mapper.forFile)
    override val binaryMapper: Mapper[File] = mapped(Mapper.forFile)
    override val binaryStampMapper: Mapper[Stamp] = mapped(Mapper.forStamp)
    override val productStampMapper: Mapper[Stamp] = mapped(Mapper.forStamp)
    override val sourceStampMapper: Mapper[Stamp] = mapped(Mapper.forStamp)

    private def mapped[V](mapper: Mapper[V]): Mapper[V] = {
      val HEADER = Random.nextInt(20000).toString
      val Regexp = s"#(\\d+)#(.+)".r
      Mapper(
        v => mapper.read(v match {
          case Regexp(HEADER, original) => original
          case Regexp(badHeader, original) =>
            throw new RuntimeException(s"Headers don't match expected: '$HEADER' got '$badHeader'")
          case _ =>
            throw new RuntimeException(s"Value '$v' cannot is used")
        }),
        v => s"#$HEADER#${mapper.write(v)}"
      )
    }
  }

  override def format = new TextAnalysisFormat(TestMapper)

  override protected def checkAnalysis(analysis: Analysis): Prop = {
    def checkFormatFail(readFormat: TextAnalysisFormat, writeFormat: TextAnalysisFormat): Prop = {
      val analisisMatch: Boolean = Try {
        val (newAnalysis, newSetup) = deserialize(serialize(analysis, writeFormat), readFormat)

        newAnalysis != analysis && newSetup != commonSetup
      }.getOrElse(false)
      analisisMatch == false
    }

    ("READ mappeped by standard" |: checkFormatFail(TextAnalysisFormat, format)) &&
      ("READ standard by mapped" |: checkFormatFail(format, TextAnalysisFormat)) &&
      super.checkAnalysis(analysis)
  }
}
