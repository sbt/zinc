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

package sbt.internal.inc

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole

/**
 * A fresh `LookupImpl` and then `queryCount` lookups, half hits and half misses. Run with
 * `zincBenchmarks/Jmh/run -p queryCount=1 sbt.internal.inc.LookupAnalysisBenchmark` to measure
 * the first lookup alone.
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
class LookupAnalysisBenchmark:
  import LookupAnalysisBenchmark.*

  @Param(Array("small", "upstream-heavy")) var scenario: String = compiletime.uninitialized
  @Param(Array("10000")) var queryCount: Int = compiletime.uninitialized
  private var fixture: LookupAnalysisFixture.Fixture = compiletime.uninitialized
  private var queries: Array[String] = compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    val dimensions = scenarios(scenario)
    fixture = dimensions.fixture()
    queries = dimensions.queries(queryCount)

  @Benchmark
  def lifecycle(bh: Blackhole): Unit =
    val lookup = fixture.newLookup()
    var i = 0
    while i < queries.length do
      bh.consume(lookup.lookupAnalysis(queries(i)))
      i += 1
end LookupAnalysisBenchmark

object LookupAnalysisBenchmark:
  /** `classpathSize` entries; the first `analysisCount` have `classesPerAnalysis` classes each. */
  final case class Scenario(classpathSize: Int, analysisCount: Int, classesPerAnalysis: Int):
    def binaryName(analysisIndex: Int, classIndex: Int): String =
      s"upstream$analysisIndex.pkg.Class$classIndex"

    def fixture(): LookupAnalysisFixture.Fixture =
      LookupAnalysisFixture.Fixture(Vector.tabulate(classpathSize) { i =>
        Option.when(i < analysisCount)(
          LookupAnalysisFixture.analysis(Vector.tabulate(classesPerAnalysis)(binaryName(i, _)))
        )
      })

    /** Even positions hit a random class, and odd positions miss. */
    def queries(count: Int): Array[String] =
      val random = new scala.util.Random(593L)
      Array.tabulate(count) { i =>
        if analysisCount == 0 || i % 2 == 1 then s"absent.Class$i"
        else binaryName(random.nextInt(analysisCount), random.nextInt(classesPerAnalysis))
      }

  val scenarios: Map[String, Scenario] = Map(
    "small" -> Scenario(32, 2, 100),
    "upstream-heavy" -> Scenario(2000, 1000, 100),
  )
end LookupAnalysisBenchmark
