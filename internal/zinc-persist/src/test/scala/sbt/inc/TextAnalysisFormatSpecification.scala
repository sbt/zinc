package sbt
package inc

import xsbti.api._
import xsbti.compile._
import xsbti.{ Problem, T2 }
import sbt.internal.inc._
import sbt.util.InterfaceUtil._
import java.io.{ BufferedReader, File, StringReader, StringWriter }

import Analysis.NonLocalProduct

import org.scalacheck._
import Prop._

object DefaultTextAnalysisFormatTest extends Properties("TextAnalysisFormat") with BaseTextAnalysisFormatTest {
  override def format = TextAnalysisFormat
}

trait BaseTextAnalysisFormatTest {
  self: Properties =>

  def format: TextAnalysisFormat

  val storeApis = true
  val dummyOutput = new xsbti.compile.SingleOutput { def outputDirectory: java.io.File = new java.io.File("/dummy") }
  val commonSetup = new MiniSetup(dummyOutput, new MiniOptions(Array(), Array(), Array()), "2.10.4", xsbti.compile.CompileOrder.Mixed, storeApis,
    Array(t2(("key", "value"))))
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

    import TestCaseGenerators._

    def f(s: String) = new File("/temp/" + s)
    val aScala = f("A.scala")
    val aClass = genClass("A").sample.get
    val cClass = genClass("C").sample.get
    val exists = new Exists(true)
    val sourceInfos = SourceInfos.makeInfo(Nil, Nil)

    var analysis = Analysis.empty
    val products = NonLocalProduct("A", "A", f("A.class"), exists) ::
      NonLocalProduct("A$", "A$", f("A$.class"), exists) :: Nil
    val binaryDeps = (f("x.jar"), "x", exists) :: Nil
    val externalDeps = new ExternalDependency("A", "C", cClass, DependencyContext.DependencyByMemberRef) :: Nil
    analysis = analysis.addSource(aScala, Seq(aClass), exists, sourceInfos, products, Nil, Nil, externalDeps, binaryDeps)

    checkAnalysis(analysis)
  }

  property("Write and read complex Analysis") =
    forAllNoShrink(TestCaseGenerators.genAnalysis)(checkAnalysis)

  // Compare two analyses with useful labelling when they aren't equal.
  protected def compare(left: Analysis, right: Analysis): Prop = {
    ("STAMPS" |: left.stamps =? right.stamps) &&
      ("APIS" |: left.apis =? right.apis) &&
      ("RELATIONS" |: left.relations =? right.relations) &&
      ("SourceInfos" |: mapInfos(left.infos) =? mapInfos(right.infos)) &&
      ("Whole Analysis" |: left =? right)
  }

  private def mapInfos(a: SourceInfos): Map[File, (Seq[Problem], Seq[Problem])] =
    a.allInfos.map {
      case (f, infos) =>
        f -> (infos.reportedProblems -> infos.unreportedProblems)
    }

  private def compareOutputs(left: Output, right: Output): Prop = {
    (left, right) match {
      case (l: SingleOutput, r: SingleOutput) =>
        "Single output dir" |: l.outputDirectory() =? r.outputDirectory()
      case (l: MultipleOutput, r: MultipleOutput) =>
        "Output group match" |: l.outputGroups() =? r.outputGroups()
      case _ =>
        s"Cannot compare $left with $right" |: left =? right
    }
  }

  // Compare two analyses with useful labelling when they aren't equal.
  protected def compare(left: MiniSetup, right: MiniSetup): Prop = {
    ("OUTPUT EQUAL" |: compareOutputs(left.output(), right.output())) &&
      ("OPTIONS EQUAL" |: left.options() =? right.options()) &&
      ("COMPILER VERSION EQUAL" |: left.compilerVersion() == right.compilerVersion) &&
      ("COMPILE ORDER EQUAL" |: left.order() =? right.order()) &&
      ("STORE API EQUAL" |: left.storeApis() =? right.storeApis()) &&
      ("EXTEA EQUAL" |: mapExtra(left.extra()) =? mapExtra(right.extra()))
  }

  private def mapExtra(extra: Array[T2[String, String]]): Seq[(String, String)] =
    extra.toSeq.map(x => (x.get1(), x.get2()))

}