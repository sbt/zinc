/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc.binary

import java.nio.file.Paths

import org.scalacheck.{ Prop, Properties }
import sbt.internal.inc.{
  Analysis,
  AnalysisGenerators,
  ConcreteAnalysisContents,
  FileAnalysisStore
}
import sbt.io.IO
import xsbti.compile.AnalysisContents
import xsbti.compile.analysis.ReadWriteMappers

object BinaryMappersSpecification
    extends Properties("BinaryMappers")
    with BinaryAnalysisFormatSpecification {

  override def RootFilePath = "/tmp/localProject"
  private final val mappers: ReadWriteMappers =
    ReadWriteMappers.getMachineIndependentMappers(Paths.get(RootFilePath))

  object RelativeAnalysisGenerators extends AnalysisGenerators {
    override def RootFilePath = BinaryMappersSpecification.RootFilePath
  }

  private final val ReadFeedback = "The analysis file cannot be read."
  override protected def checkAnalysis(analysis: Analysis): Prop = {
    import sbt.internal.inc.JavaInterfaceUtil.EnrichOptional
    // Note: we test writing to the file directly to reuse `FileBasedStore` as it is written
    val readContents = IO.withTemporaryFile("analysis", "test") { tempAnalysisFile =>
      val fileBasedStore = FileAnalysisStore.binary(tempAnalysisFile, mappers)
      val contents = AnalysisContents.create(analysis, commonSetup)
      fileBasedStore.set(contents)
      fileBasedStore.get().toOption.getOrElse(sys.error(ReadFeedback))
    }

    val readAnalysis = readContents.getAnalysis match { case a: Analysis => a }
    compare(analysis, readAnalysis) && compare(commonSetup, readContents.getMiniSetup)
  }

  property("The default relative mapper works in empty analysis files") = {
    checkAnalysis(Analysis.empty)
  }

  property("The default relative mapper works in complex analysis files") = {
    import org.scalacheck.Prop.forAllNoShrink
    forAllNoShrink(RelativeAnalysisGenerators.genAnalysis)(checkAnalysis)
  }
}
