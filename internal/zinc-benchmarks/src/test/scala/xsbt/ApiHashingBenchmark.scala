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

package xsbt

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import xsbt.api._
import xsbti.api.{ ClassLike, DefinitionType }

/**
 * The per-class work `AnalysisCallback.api` does on a corpus written by [[ApiCorpus]], with the
 * current `HashAPI` and `NameHashing` and with their legacy copies.
 *
 * Run with `zincBenchmarks/jmh:run -p corpus=<file> -prof gc xsbt.ApiHashingBenchmark`. Without a
 * corpus it hashes generated classes, which only checks that it runs.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgs = Array("-Xmx4g", "-Xss16m"))
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
class ApiHashingBenchmark {
  @Param(Array("")) var corpus: String = _
  @Param(Array("false")) var optimizedSealed: Boolean = _
  var classes: Array[ClassLike] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    classes =
      if (corpus.nonEmpty) ApiCorpus.read(Paths.get(corpus))
      else
        // Without a corpus, as in the CI smoke run, hash generated classes instead.
        Array.tabulate(1000) { i =>
          HashingEquivalenceGenerators.genClass
            .pureApply(org.scalacheck.Gen.Parameters.default, org.scalacheck.rng.Seed(i.toLong))
        }
  }

  @Benchmark
  def callback(bh: Blackhole): Unit = {
    var i = 0
    while (i < classes.length) {
      val c = classes(i)
      bh.consume(APIUtil.hasMacro(c))
      bh.consume(APIUtil.minimize(c))
      bh.consume(HashAPI(c))
      bh.consume(new NameHashing(optimizedSealed).nameHashes(c))
      if (c.definitionType == DefinitionType.Trait)
        bh.consume(HashAPI(_.hashAPI(c), includePrivateDefsInTrait = true))
      i += 1
    }
  }

  @Benchmark
  def callbackLegacy(bh: Blackhole): Unit = {
    var i = 0
    while (i < classes.length) {
      val c = classes(i)
      bh.consume(APIUtil.hasMacro(c))
      bh.consume(APIUtil.minimize(c))
      bh.consume(LegacyHashAPI(c))
      bh.consume(new LegacyNameHashing(optimizedSealed).nameHashes(c))
      if (c.definitionType == DefinitionType.Trait)
        bh.consume(LegacyHashAPI(_.hashAPI(c), includePrivateDefsInTrait = true))
      i += 1
    }
  }

  @Benchmark
  def nameHashes(bh: Blackhole): Unit = {
    var i = 0
    while (i < classes.length) {
      bh.consume(new NameHashing(optimizedSealed).nameHashes(classes(i)))
      i += 1
    }
  }

  @Benchmark
  def nameHashesLegacy(bh: Blackhole): Unit = {
    var i = 0
    while (i < classes.length) {
      bh.consume(new LegacyNameHashing(optimizedSealed).nameHashes(classes(i)))
      i += 1
    }
  }

  @Benchmark
  def apiHash(bh: Blackhole): Unit = {
    var i = 0
    while (i < classes.length) {
      bh.consume(HashAPI(classes(i)))
      i += 1
    }
  }

  @Benchmark
  def apiHashLegacy(bh: Blackhole): Unit = {
    var i = 0
    while (i < classes.length) {
      bh.consume(LegacyHashAPI(classes(i)))
      i += 1
    }
  }
}
