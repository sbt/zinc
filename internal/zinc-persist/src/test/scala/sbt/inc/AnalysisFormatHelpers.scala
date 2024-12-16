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

package sbt.inc

import java.io.File

import org.scalacheck._
import org.scalacheck.Prop._
import sbt.internal.inc.Analysis.NonLocalProduct
import sbt.internal.inc.SourceInfos.emptyInfo
import sbt.internal.inc._
import sbt.internal.inc.AnalysisGenerators._
import sbt.io.IO
import sbt.util.InterfaceUtil._
import xsbti.api._
import xsbti.api.DependencyContext.DependencyByMemberRef
import xsbti.compile.{ FileAnalysisStore => _, _ }
import xsbti.compile.CompileOrder._
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.{ T2, VirtualFileRef }

object AnalysisFormatHelpers {
  val mappers: ReadWriteMappers = ReadWriteMappers.getMachineIndependentMappers(RootFilePath)

  val commonSetup: MiniSetup = {
    val output: Output = CompileOutput(RootFilePath.resolve("out"))
    val opts = MiniOptions.of(Array(), Array(), Array())
    MiniSetup.of(output, opts, "2.10.4", Mixed, true, Array(t2("key" -> "value")))
  }

  private val simpleAnalysis: Analysis = {
    def vf(s: String) = VirtualFileRef.of(s)
    def cls(s: String) = genClass(s).sample.get
    def emptyStamp = FarmHash.fromLong(1L)
    val analysis0 = Analysis.empty.addSource(
      src = vf(s"$RootFilePath/A.scala"),
      apis = Seq(cls("A")),
      stamp = emptyStamp,
      info = emptyInfo,
      nonLocalProducts = NonLocalProduct("A", "A", vf("A.class"), emptyStamp) ::
        NonLocalProduct("A$", "A$", vf("A$.class"), emptyStamp) :: Nil,
      localProducts = Nil,
      internalDeps = Nil,
      externalDeps = ExternalDependency.of("A", "C", cls("C"), DependencyByMemberRef) :: Nil,
      libraryDeps = (vf("x.jar"), "x", emptyStamp) :: Nil
    )
    analysis0.copy(compilations = analysis0.compilations.add(Compilation(1L, commonSetup.output())))
  }

  def forEmpty(f: Analysis => Prop): Prop = f(Analysis.empty)
  def forSimple(f: Analysis => Prop): Prop = f(simpleAnalysis)
  def forComplex(f: Analysis => Prop): Prop = forAllNoShrink(genAnalysis)(f)

  def checkStoreRoundtrip(analysis: Analysis, store: File => AnalysisStore) = {
    val contents = AnalysisContents.create(analysis, commonSetup)
    val readContents = IO.withTemporaryFile("analysis", "test") { analysisFile =>
      val analysisStore = store(analysisFile)
      analysisStore.set(contents)
      analysisStore
        .get()
        .orElseThrow(() => new RuntimeException("The analysis file cannot be read."))
    }
    compare(analysis, readContents.getAnalysis) && compare(commonSetup, readContents.getMiniSetup)
  }

  def compare(left: CompileAnalysis, right: CompileAnalysis): Prop =
    (left, right) match {
      case (left: Analysis, right: Analysis) => compare(left, right)
      case _                                 => falsified :| s"left: $left\n  right: $right"
    }

  // Compare two analyses with useful labelling when they aren't equal.
  private def compare(left: Analysis, right: Analysis): Prop = {
    ("STAMPS" |: left.stamps =? right.stamps) &&
    ("APIS" |: left.apis =? right.apis) &&
    ("RELATIONS" |: left.relations =? right.relations) &&
    ("SourceInfos" |: mapInfos(left.infos) =? mapInfos(right.infos)) &&
    ("Whole Analysis" |: left =? right)
  }

  // Compare two analyses with useful labelling when they aren't equal.
  def compare(left: MiniSetup, right: MiniSetup): Prop = {
    ("OPTIONS EQUAL" |: left.options() =? right.options()) &&
    ("COMPILER VERSION EQUAL" |: left.compilerVersion() =? right.compilerVersion) &&
    ("COMPILE ORDER EQUAL" |: left.order() =? right.order()) &&
    ("STORE API EQUAL" |: left.storeApis() =? right.storeApis()) &&
    ("EXTRA EQUAL" |: mapExtra(left.extra()) =? mapExtra(right.extra()))
  }

  private def mapInfos(a: SourceInfos) = a.allInfos.map {
    case (f, sourceInfo) =>
      import sourceInfo._
      f -> ((getReportedProblems.toList, getUnreportedProblems.toList, getMainClasses.toList))
  }

  private def mapExtra(extra: Array[T2[String, String]]): Seq[(String, String)] =
    extra.toSeq.map(x => (x.get1(), x.get2()))
}
