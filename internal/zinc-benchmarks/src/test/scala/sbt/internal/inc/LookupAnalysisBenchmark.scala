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
import xsbti.VirtualFileRef

/**
 * `queryCount` lookups, half hits and half misses, over `analysisCount` upstream analyses.
 * `indexed` builds the index first, as each compile does, and `linear` is the scan it replaced.
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
class LookupAnalysisBenchmark:
  @Param(Array("1000")) var analysisCount: Int = 0
  @Param(Array("50")) var classesPerAnalysis: Int = 0
  @Param(Array("100", "10000")) var queryCount: Int = 0
  private var analyses: Vector[Analysis] = compiletime.uninitialized
  private var queries: Array[String] = compiletime.uninitialized

  private def binaryName(analysisIndex: Int, classIndex: Int): String =
    s"upstream$analysisIndex.pkg.Class$classIndex"

  @Setup
  def setup(): Unit =
    analyses = Vector.tabulate(analysisCount): i =>
      val classes = Vector.tabulate(classesPerAnalysis)(c => s"source$i.$c" -> binaryName(i, c))
      Analysis.empty.copy(relations = Relations.empty.addClasses(VirtualFileRef.of("src"), classes))
    val random = new scala.util.Random(593L)
    queries = Array.tabulate(queryCount): i =>
      if i % 2 == 1 then s"absent.Class$i"
      else binaryName(random.nextInt(analysisCount), random.nextInt(classesPerAnalysis))

  @Benchmark
  def linear(bh: Blackhole): Unit =
    queries.foreach: name =>
      bh.consume(analyses.find(_.relations.productClassName._2s.contains(name)))

  @Benchmark
  def indexed(bh: Blackhole): Unit =
    val index = LookupImpl.indexByBinaryClassName(analyses)
    queries.foreach(name => bh.consume(index.get(name)))
end LookupAnalysisBenchmark
