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
  object TestMapper extends AnalysisMappersAdapter {
    override val sourceMapper: Mapper[File] = mapped(Mapper.forFile)
    override val productMapper: Mapper[File] = mapped(Mapper.forFile)
    override val binaryMapper: Mapper[File] = mapped(Mapper.forFile)
    override val binaryStampMapper: ContextAwareMapper[File, Stamp] = contextedMapped(Mapper.forStamp)
    override val productStampMapper: ContextAwareMapper[File, Stamp] = contextedMapped(Mapper.forStamp)
    override val sourceStampMapper: ContextAwareMapper[File, Stamp] = contextedMapped(Mapper.forStamp)

    private class TestRandomizer[V] {
      val HEADER = Random.nextInt(20000).toString
      val Regexp = s"#(\\d+)#(.+)".r

      def read(v: String): String = v match {
        case Regexp(HEADER, original) => original
        case Regexp(badHeader, original) =>
          throw new RuntimeException(s"Headers don't match expected: '$HEADER' got '$badHeader'")
        case Regexp(list @ _*) =>
          throw new RuntimeException(s"Value '$v' cannot be used. Matched: $list")
        case _ =>
          throw new RuntimeException(s"Value '$v' cannot be used. Does not match ${Regexp.pattern.pattern()}")
      }

      def write(v: String) = s"#$HEADER#${v}"
    }

    private def mapped[V](mapper: Mapper[V]): Mapper[V] = {
      val randomizer = new TestRandomizer[V]
      Mapper(
        v => mapper.read(randomizer.read(v)),
        v => randomizer.write(mapper.write(v))
      )
    }

    private def contextedMapped[C, V](mapper: ContextAwareMapper[C, V]): ContextAwareMapper[C, V] = {
      val valueRanomizer = new TestRandomizer[V]

      ContextAwareMapper(
        (c, s) => mapper.read(c, valueRanomizer.read(s).drop(c.toString.length + 1)),
        (c, v) => valueRanomizer.write(s"$c#${mapper.write(c, v)}")
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
