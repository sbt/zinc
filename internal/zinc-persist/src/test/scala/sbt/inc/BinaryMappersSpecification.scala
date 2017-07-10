package sbt.inc

import java.nio.file.Paths

import org.scalacheck.{ Prop, Properties }
import sbt.internal.inc.{
  Analysis,
  AnalysisGenerator,
  ConcreteAnalysisContents,
  FileAnalysisStore
}
import sbt.io.IO

object BinaryMappersSpecification
    extends Properties("BinaryMappers")
    with BinaryAnalysisFormatSpecification {

  override def RootFilePath = "/tmp/localProject"
  private final val mappers: ReadWriteMappers =
    ReadWriteMappers.getMachineIndependentMappers(Paths.get(RootFilePath))

  object RelativeAnalysisGenerator extends AnalysisGenerator {
    override def RootFilePath = BinaryMappersSpecification.RootFilePath
  }

  private final val ReadFeedback = "The analysis file cannot be read."
  override protected def checkAnalysis(analysis: Analysis): Prop = {
    import sbt.internal.inc.JavaInterfaceUtil.PimpOptional
    // Note: we test writing to the file directly to reuse `FileBasedStore` as it is written
    val readContents = IO.withTemporaryFile("analysis", "test") { tempAnalysisFile =>
      val fileBasedStore = FileAnalysisStore(tempAnalysisFile, mappers)
      val contents = ConcreteAnalysisContents(analysis, commonSetup)
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
    forAllNoShrink(RelativeAnalysisGenerator.genAnalysis)(checkAnalysis)
  }
}
