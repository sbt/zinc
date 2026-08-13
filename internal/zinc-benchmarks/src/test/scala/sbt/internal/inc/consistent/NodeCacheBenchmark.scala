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

package sbt.internal.inc.consistent

import java.util.HashMap
import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import xsbti.api.ParameterRef
import xsbti.api.Projection

@BenchmarkMode(Array(Mode.AverageTime))
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class NodeCacheBenchmark {
  @Param(Array("1024"))
  var size: Int = 0

  var first: Array[Projection] = compiletime.uninitialized
  var second: Array[Projection] = compiletime.uninitialized

  @Setup
  def setup(): Unit = {
    require(size > 0 && (size & (size - 1)) == 0)
    val bits = Integer.numberOfTrailingZeros(size)
    val prefix = ParameterRef.of("P")
    val names = Array.tabulate(size) { value =>
      val name = new StringBuilder(bits * 2)
      var bit = 0
      while (bit < bits) {
        name.append(if (((value >>> bit) & 1) == 0) "Aa" else "BB")
        bit += 1
      }
      name.result()
    }
    require(names.distinct.length == size)
    require(names.iterator.map(_.hashCode).toSet.size == 1)
    first = names.map(Projection.of(prefix, _))
    second = names.map(Projection.of(prefix, _))
    require(first.iterator.map(_.hashCode).toSet.size == 1)
  }

  @Benchmark
  def legacyHashMap(bh: Blackhole): Unit = {
    val cache = new HashMap[AnyRef, AnyRef]()
    var i = 0
    while (i < size) {
      bh.consume(cache.putIfAbsent(first(i), first(i)))
      i += 1
    }
    i = 0
    while (i < size) {
      bh.consume(cache.putIfAbsent(second(i), second(i)))
      i += 1
    }
  }

  @Benchmark
  def zincNodeCache(bh: Blackhole): Unit = {
    val cache = new NodeCache
    var i = 0
    while (i < size) {
      bh.consume(cache.intern(first(i)))
      i += 1
    }
    i = 0
    while (i < size) {
      bh.consume(cache.intern(second(i)))
      i += 1
    }
  }
}
