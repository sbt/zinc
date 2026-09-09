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

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
class LookupAnalysisBenchmark {
  @Param(Array("empty", "small", "library-heavy", "upstream-heavy", "class-heavy"))
  var scenario: String = ""

  @Param(Array("mixed"))
  var queryMix: String = ""

  @Param(Array("10000"))
  var queryCount: Int = 0

  private var fixture: LookupAnalysisFixture.Fixture = compiletime.uninitialized
  private var queries: Array[String] = compiletime.uninitialized
  private var warmLookup: LookupImpl = compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit = {
    val dimensions = LookupAnalysisFixture.scenarios(scenario)
    fixture = dimensions.fixture()
    require(fixture.classpath.size == dimensions.classpathSize)
    require(fixture.analyses.size == dimensions.analysisCount)
    require(fixture.analyses.forall(
      _.relations.productClassName._2s.size == dimensions.classesPerAnalysis
    ))
    queries = dimensions.queries(queryMix, queryCount)
    val names = fixture.analyses.iterator.flatMap(_.relations.productClassName._2s).toSet
    val expectedHits =
      if (dimensions.analysisCount == 0 || queryMix == "miss") 0
      else if (queryMix == "mixed") (queryCount + 1) / 2
      else queryCount
    require(queries.count(names.contains) == expectedHits)
    warmLookup = fixture.newLookup()
    warmLookup.lookupAnalysis(queries(0))
  }

  // One operation is a queryCount-sized batch; divide time/allocation by queryCount for per-query data.
  @Benchmark
  def warmBatch(bh: Blackhole): Unit = runQueries(warmLookup, bh)

  // One operation includes construction and the first query, including all lazy initialization.
  @Benchmark
  def firstLookup(bh: Blackhole): Unit =
    bh.consume(fixture.newLookup().lookupAnalysis(queries(0)))

  // One operation includes a fresh lookup and queryCount queries; fixture construction is excluded.
  @Benchmark
  def lifecycle(bh: Blackhole): Unit = runQueries(fixture.newLookup(), bh)

  private def runQueries(lookup: LookupImpl, bh: Blackhole): Unit = {
    var i = 0
    while (i < queries.length) {
      bh.consume(lookup.lookupAnalysis(queries(i)))
      i += 1
    }
  }
}
