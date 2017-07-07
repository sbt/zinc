package sbt.inc

import xsbti.api._
import xsbti.compile._
import xsbti.{ Problem, T2 }
import sbt.internal.inc._
import sbt.util.InterfaceUtil._
import java.io.File

import Analysis.NonLocalProduct
import org.scalacheck._
import Prop._
import sbt.io.IO

object BinaryAnalysisFormatSpecification
    extends Properties("BinaryAnalysisFormat")
    with BinaryAnalysisFormatSpecification {

  property("Write and read empty Analysis") = {
    checkAnalysis(Analysis.empty)
  }

  val simpleAnalysis: Analysis = {
    import TestCaseGenerators._
    def f(s: String) = new File(s"$RootFilePath/s")
    val aScala = f("A.scala")
    val aClass = genClass("A").sample.get
    val cClass = genClass("C").sample.get
    val stamp = EmptyStamp
    val infos = SourceInfos.makeInfo(Nil, Nil, Nil)

    val apis = Seq(aClass)
    val products = NonLocalProduct("A", "A", f("A.class"), stamp) ::
      NonLocalProduct("A$", "A$", f("A$.class"), stamp) :: Nil
    val binDeps = (f("x.jar"), "x", stamp) :: Nil
    val memberDep = DependencyContext.DependencyByMemberRef
    val extDeps = new ExternalDependency("A", "C", cClass, memberDep) :: Nil
    Analysis.empty.addSource(aScala, apis, stamp, infos, products, Nil, Nil, extDeps, binDeps)
  }

  property("Write and read simple Analysis") = {
    checkAnalysis(simpleAnalysis)
  }

  property("Write and read complex Analysis") =
    forAllNoShrink(TestCaseGenerators.genAnalysis)(checkAnalysis)

}

trait BinaryAnalysisFormatSpecification { self: Properties =>
  val storeApis = true
  def RootFilePath: String = "/dummy"
  def dummyOutput = new xsbti.compile.SingleOutput {
    def getOutputDirectory: java.io.File = new java.io.File(RootFilePath)
  }

  private final val ScalaVersion = "2.10.4"
  val commonSetup: MiniSetup = {
    val output = dummyOutput
    val options = new MiniOptions(Array(), Array(), Array())
    val order = xsbti.compile.CompileOrder.Mixed
    val shouldStoreApis = storeApis
    val extra = Array(t2("key" -> "value"))
    new MiniSetup(output, options, ScalaVersion, order, shouldStoreApis, extra)
  }

  private final val ReadFeedback = "The analysis file cannot be read."
  protected def checkAnalysis(analysis: Analysis): Prop = {
    // Note: we test writing to the file directly to reuse `FileBasedStore` as it is written
    val (readAnalysis0, readSetup) = IO.withTemporaryFile("analysis", "test") { tempAnalysisFile =>
      val fileBasedStore = FileBasedStore.binary(tempAnalysisFile)
      fileBasedStore.set(analysis, commonSetup)
      fileBasedStore.get().getOrElse(sys.error(ReadFeedback))
    }
    val readAnalysis = readAnalysis0 match { case a: Analysis => a }
    compare(analysis, readAnalysis) && compare(commonSetup, readSetup)
  }

  // Compare two analyses with useful labelling when they aren't equal.
  protected def compare(left: Analysis, right: Analysis): Prop = {
    ("STAMPS" |: left.stamps =? right.stamps) &&
    ("APIS" |: left.apis =? right.apis) &&
    ("RELATIONS" |: left.relations =? right.relations) &&
    ("SourceInfos" |: mapInfos(left.infos) =? mapInfos(right.infos)) &&
    ("Whole Analysis" |: left =? right)
  }

  private def mapInfos(a: SourceInfos): Map[File, (Seq[Problem], Seq[Problem], Seq[String])] =
    a.allInfos.map {
      case (f, infos) =>
        f -> ((
                infos.getReportedProblems.toList,
                infos.getUnreportedProblems.toList,
                infos.getMainClasses.toList
              ))
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
