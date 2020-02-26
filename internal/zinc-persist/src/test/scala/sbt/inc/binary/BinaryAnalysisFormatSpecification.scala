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

package sbt.inc.binary

import java.io.File
import java.nio.file.{ Path, Paths }

import org.scalacheck.Prop._
import org.scalacheck._
import sbt.internal.inc.Analysis.NonLocalProduct
import sbt.internal.inc._
import sbt.io.IO
import sbt.util.InterfaceUtil._
import xsbti.api._
import xsbti.compile.{ FileAnalysisStore => _, _ }
import xsbti.{ Problem, T2, VirtualFileRef }

object BinaryAnalysisFormatSpecification
    extends Properties("BinaryAnalysisFormat")
    with BinaryAnalysisFormatSpecificationHelpers {

  property("Write and read empty Analysis") = {
    checkAnalysis(Analysis.empty)
  }

  val simpleAnalysis: Analysis = {
    import AnalysisGenerators._
    def f(s: String) = new File(s"$RootFilePath/$s")
    def vf(s: String) = VirtualFileRef.of(s)
    val aScala = f("A.scala")
    val aClass = genClass("A").sample.get
    val cClass = genClass("C").sample.get
    val stamp = FarmHash.fromLong(1)
    val infos = SourceInfos.makeInfo(Nil, Nil, Nil)

    val apis = Seq(aClass)
    val products = NonLocalProduct("A", "A", vf("A.class"), stamp) ::
      NonLocalProduct("A$", "A$", vf("A$.class"), stamp) :: Nil
    val libDeps = (vf("x.jar"), "x", stamp) :: Nil
    val memberDep = DependencyContext.DependencyByMemberRef
    val extDeps = ExternalDependency.of("A", "C", cClass, memberDep) :: Nil
    Analysis.empty.addSource(
      VirtualFileRef.of(aScala.toString),
      apis,
      stamp,
      infos,
      products,
      Nil,
      Nil,
      extDeps,
      libDeps
    )
  }

  property("Write and read simple Analysis") = {
    checkAnalysis(simpleAnalysis)
  }

  property("Write and read complex Analysis") =
    forAllNoShrink(AnalysisGenerators.genAnalysis)(checkAnalysis)

}

trait BinaryAnalysisFormatSpecificationHelpers { self: Properties =>
  val storeApis = true
  def RootFilePath: String = "/dummy"
  def dummyOutput = new xsbti.compile.SingleOutput {
    def getOutputDirectory: Path = Paths.get(RootFilePath)
  }

  private final val ScalaVersion = "2.10.4"
  val commonSetup: MiniSetup = {
    val output = dummyOutput
    val options = MiniOptions.of(Array(), Array(), Array())
    val order = xsbti.compile.CompileOrder.Mixed
    val shouldStoreApis = storeApis
    val extra = Array(t2("key" -> "value"))
    MiniSetup.of(output, options, ScalaVersion, order, shouldStoreApis, extra)
  }

  private final val ReadFeedback = "The analysis file cannot be read."
  protected def checkAnalysis(analysis: Analysis): Prop = {
    import JavaInterfaceUtil.EnrichOptional
    // Note: we test writing to the file directly to reuse `FileBasedStore` as it is written
    val readContents = IO.withTemporaryFile("analysis", "test") { tempAnalysisFile =>
      val fileBasedStore = FileAnalysisStore.binary(tempAnalysisFile)
      val contents = AnalysisContents.create(analysis, commonSetup)
      fileBasedStore.set(contents)
      fileBasedStore.get().toOption.getOrElse(sys.error(ReadFeedback))
    }
    val readAnalysis = readContents.getAnalysis match { case a: Analysis => a }
    compare(analysis, readAnalysis) && compare(commonSetup, readContents.getMiniSetup)
  }

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
