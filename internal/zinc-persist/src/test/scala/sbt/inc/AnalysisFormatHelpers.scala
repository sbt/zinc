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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.Analysis.NonLocalProduct
import sbt.internal.inc.SourceInfos.emptyInfo
import sbt.internal.inc.*
import sbt.internal.inc.AnalysisGenerators.*
import sbt.io.IO
import sbt.util.InterfaceUtil.*
import xsbti.api.*
import xsbti.api.DependencyContext.DependencyByMemberRef
import xsbti.compile.{ FileAnalysisStore as _, * }
import xsbti.compile.CompileOrder.*
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.{ T2, VirtualFileRef }

object AnalysisFormatHelpers:
  val mappers: ReadWriteMappers = ReadWriteMappers.getMachineIndependentMappers(RootFilePath)

  val commonSetup: MiniSetup =
    val output: Output = CompileOutput(RootFilePath.resolve("out"))
    val opts = MiniOptions.of(Array(), Array(), Array())
    MiniSetup.of(output, opts, "2.10.4", Mixed, true, Array(t2("key" -> "value")))

  private val simpleAnalysis: Analysis =
    def vf(s: String) = VirtualFileRef.of(s)
    def cls(s: String) = HedgehogSupport.sample(genClass(s), s.hashCode.toLong)
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

  def forEmpty(f: Analysis => Result): Result = f(Analysis.empty)
  def forSimple(f: Analysis => Result): Result = f(simpleAnalysis)
  def forComplex(f: Analysis => Result): Property =
    for analysis <- genAnalysis.forAll
    yield f(analysis)

  /** The round trips of the empty, a simple, and generated analyses. */
  def roundTrips(check: Analysis => Result): List[Test] = List(
    example("round-trip empty", forEmpty(check)),
    example("round-trip simple", forSimple(check)),
    property("round-trip complex", forComplex(check)).noShrinking,
  )

  def checkStoreRoundtrip(analysis: Analysis, store: File => AnalysisStore) =
    val contents = AnalysisContents.create(analysis, commonSetup)
    val readContents = IO.withTemporaryFile("analysis", "test") { analysisFile =>
      val analysisStore = store(analysisFile)
      analysisStore.set(contents)
      analysisStore
        .get()
        .orElseThrow(() => new RuntimeException("The analysis file cannot be read."))
    }
    compare(analysis, readContents.getAnalysis).and(
      compare(commonSetup, readContents.getMiniSetup)
    )

  def compare(left: CompileAnalysis, right: CompileAnalysis): Result =
    (left, right) match
      case (left: Analysis, right: Analysis) => compare(left, right)
      case _                                 => Result.failure.log(s"left: $left\n  right: $right")

  // Compare two analyses with useful labelling when they aren't equal.
  private def compare(left: Analysis, right: Analysis): Result =
    Result.all(
      List(
        (left.stamps ==== right.stamps).log("STAMPS"),
        (left.apis ==== right.apis).log("APIS"),
        (left.relations ==== right.relations).log("RELATIONS"),
        (mapInfos(left.infos) ==== mapInfos(right.infos)).log("SourceInfos"),
        (left ==== right).log("Whole Analysis"),
      )
    )

  // Compare two analyses with useful labelling when they aren't equal.
  def compare(left: MiniSetup, right: MiniSetup): Result =
    Result.all(
      List(
        (left.options() ==== right.options()).log("OPTIONS EQUAL"),
        (left.compilerVersion() ==== right.compilerVersion).log("COMPILER VERSION EQUAL"),
        (left.order() ==== right.order()).log("COMPILE ORDER EQUAL"),
        (left.storeApis() ==== right.storeApis()).log("STORE API EQUAL"),
        (mapExtra(left.extra()) ==== mapExtra(right.extra())).log("EXTRA EQUAL"),
      )
    )

  private def mapInfos(a: SourceInfos) = a.allInfos.map {
    case (f, sourceInfo) =>
      import sourceInfo.*
      f -> ((getReportedProblems.toList, getUnreportedProblems.toList, getMainClasses.toList))
  }

  private def mapExtra(extra: Array[T2[String, String]]): Seq[(String, String)] =
    extra.toSeq.map(x => (x.get1(), x.get2()))
end AnalysisFormatHelpers
