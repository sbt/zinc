/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.inc.text

import java.io.{ BufferedReader, File, StringReader, StringWriter }
import java.nio.file.{ Path, Paths }

import org.scalacheck.Prop._
import org.scalacheck._
import sbt.internal.inc.Analysis.NonLocalProduct
import sbt.internal.inc._
import sbt.internal.inc.text.TextAnalysisFormat
import sbt.io.IO
import sbt.util.InterfaceUtil._
import xsbti.api._
import xsbti.compile.{ FileAnalysisStore => _, _ }
import xsbti.{ Problem, T2, VirtualFileRef }

object TextAnalysisFormatSpecification
    extends Properties("TextAnalysisFormat")
    with BaseTextAnalysisFormatTest {

  override val analysisGenerators: AnalysisGenerators = AnalysisGenerators
  override def format = TextAnalysisFormat
  override def checkAnalysis(analysis: Analysis): Prop = {
    // Note: we test writing to the file directly to reuse `FileBasedStore` as it is written
    val readContents = IO.withTemporaryFile("analysis", "test") { tempAnalysisFile =>
      val fileBasedStore = FileAnalysisStore.text(tempAnalysisFile, format)
      fileBasedStore.set(AnalysisContents.create(analysis, commonSetup))
      fileBasedStore.unsafeGet()
    }
    val readAnalysis = readContents.getAnalysis match { case a: Analysis => a }
    compare(analysis, readAnalysis) && compare(commonSetup, readContents.getMiniSetup)
    super.checkAnalysis(analysis)
  }
}

trait BaseTextAnalysisFormatTest { self: Properties =>

  val analysisGenerators: AnalysisGenerators
  def format: TextAnalysisFormat

  val storeApis = true
  def RootFilePath: String = "/dummy"
  def dummyOutput = new xsbti.compile.SingleOutput {
    def getOutputDirectory: Path = Paths.get(RootFilePath)
  }

  val commonSetup = MiniSetup.of(
    dummyOutput,
    MiniOptions.of(Array(), Array(), Array()),
    "2.10.4",
    xsbti.compile.CompileOrder.Mixed,
    storeApis,
    Array(t2(("key", "value")))
  )
  val companionStore = new CompanionsStore {
    def getUncaught(): (Map[String, Companions], Map[String, Companions]) = (Map(), Map())
    def get(): Option[(Map[String, Companions], Map[String, Companions])] = Some(getUncaught())
  }

  protected def serialize(analysis: Analysis, format: TextAnalysisFormat): String = {
    val writer = new StringWriter
    format.write(writer, analysis, commonSetup)
    writer.toString
  }

  protected def deserialize(from: String, format: TextAnalysisFormat): (Analysis, MiniSetup) = {
    val reader = new BufferedReader(new StringReader(from))
    val (readAnalysis: Analysis, readSetup) = format.read(reader, companionStore)
    (readAnalysis, readSetup)
  }

  protected def checkAnalysis(analysis: Analysis) = {
    val (readAnalysis, readSetup) = deserialize(serialize(analysis, format), format)
    compare(analysis, readAnalysis) && compare(commonSetup, readSetup)
  }

  property("Write and read empty Analysis") = {
    checkAnalysis(Analysis.empty)
  }

  property("Write and read simple Analysis") = {

    import analysisGenerators._
    def f(s: String) = new File(s"$RootFilePath/$s")
    def vf(s: String) = VirtualFileRef.of(s)
    val aScala = f("A.scala")
    val aClass = genClass("A").sample.get
    val cClass = genClass("C").sample.get
    val absent = EmptyStamp
    val stamp = FarmHash.fromLong(1)
    val sourceInfos = SourceInfos.makeInfo(Nil, Nil, Nil)

    var analysis = Analysis.empty
    val products = NonLocalProduct("A", "A", vf("A.class"), stamp) ::
      NonLocalProduct("A$", "A$", vf("A$.class"), stamp) :: Nil
    val libraryDeps = (vf("x.jar"), "x", stamp) :: Nil
    val externalDeps = ExternalDependency.of(
      "A",
      "C",
      cClass,
      DependencyContext.DependencyByMemberRef
    ) :: Nil
    analysis = analysis.addSource(
      VirtualFileRef.of(aScala.toString),
      Seq(aClass),
      stamp,
      sourceInfos,
      products,
      Nil,
      Nil,
      externalDeps,
      libraryDeps
    )
    val compilations0 = analysis.compilations
    val compilations1 = compilations0.add(Compilation(1L, dummyOutput))
    analysis = analysis.copy(compilations = compilations1)

    checkAnalysis(analysis)
  }

  property("Write and read complex Analysis") =
    forAllNoShrink(analysisGenerators.genAnalysis)(checkAnalysis)

  // Compare two analyses with useful labelling when they aren't equal.
  protected def compare(left: Analysis, right: Analysis): Prop = {
    ("STAMPS" |: left.stamps =? right.stamps) &&
    ("APIS" |: left.apis =? right.apis) &&
    ("RELATIONS" |: left.relations =? right.relations) &&
    ("SourceInfos" |: mapInfos(left.infos) =? mapInfos(right.infos)) &&
    ("Whole Analysis" |: left =? right)
  }

  private def mapInfos(
      a: SourceInfos
  ): Map[VirtualFileRef, (Seq[Problem], Seq[Problem], Seq[String])] =
    a.allInfos.map {
      case (f, infos) =>
        f -> (
          (
            infos.getReportedProblems.toList,
            infos.getUnreportedProblems.toList,
            infos.getMainClasses.toList
          )
        )
    }

  private def compareOutputs(left: Output, right: Output): Prop = {
    (left, right) match {
      case (l: SingleOutput, r: SingleOutput) =>
        "Single output dir" |: l.getOutputDirectory() =? r.getOutputDirectory()
      case (l: MultipleOutput, r: MultipleOutput) =>
        "Output group match" |: l.getOutputGroups() =? r.getOutputGroups()
      case _ =>
        s"Cannot compare $left with $right" |: left =? right
    }
  }

  // Compare two analyses with useful labelling when they aren't equal.
  protected def compare(left: MiniSetup, right: MiniSetup): Prop = {
    ("OPTIONS EQUAL" |: left.options() =? right.options()) &&
    ("COMPILER VERSION EQUAL" |: left.compilerVersion() == right.compilerVersion) &&
    ("COMPILE ORDER EQUAL" |: left.order() =? right.order()) &&
    ("STORE API EQUAL" |: left.storeApis() =? right.storeApis()) &&
    ("EXTEA EQUAL" |: mapExtra(left.extra()) =? mapExtra(right.extra()))
  }

  private def mapExtra(extra: Array[T2[String, String]]): Seq[(String, String)] =
    extra.toSeq.map(x => (x.get1(), x.get2()))

}
