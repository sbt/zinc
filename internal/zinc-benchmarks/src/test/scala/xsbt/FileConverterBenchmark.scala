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

import java.lang.management.ManagementFactory
import java.nio.file.{ Files, Path }
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import sbt.internal.inc.MappedFileConverter
import sbt.io.IO
import xsbti.VirtualFile

@State(Scope.Benchmark)
class FileConverterState {
  @Param(Array("2000"))
  var fileCount: Int = 0

  @Param(Array("100"))
  var consumers: Int = 0

  var rootDir: Path = null
  var classesDir: Path = null
  var shared: MappedFileConverter = null

  @Setup(Level.Trial)
  def setup(): Unit = {
    rootDir = Files.createTempDirectory("file-converter-benchmark")
    classesDir = Files.createDirectories(rootDir.resolve("classes"))
    (0 until fileCount).foreach { i =>
      val pkg = Files.createDirectories(classesDir.resolve(s"pkg${i % 50}"))
      Files.write(pkg.resolve(s"Class$i.class"), Array.fill[Byte](1024)((i % 127).toByte))
    }
    shared = newConverter
  }

  @TearDown(Level.Trial)
  def teardown(): Unit = IO.delete(rootDir.toFile)

  def newConverter: MappedFileConverter = MappedFileConverter(Map("BASE" -> rootDir), true)
}

/**
 * Conversion cost of one shared classes directory.
 * `freshConverterPerConversion` approximates the pre-interning behavior (every consumer
 * re-materializes all items); `sharedConverter` is the interned steady state.
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
class FileConverterBenchmark {

  @Benchmark
  def freshConverterPerConversion(state: FileConverterState): VirtualFile =
    state.newConverter.toVirtualFile(state.classesDir)

  @Benchmark
  def sharedConverter(state: FileConverterState): VirtualFile =
    state.shared.toVirtualFile(state.classesDir)
}

object HeapMeter {
  def usedHeap(): Long = {
    val bean = ManagementFactory.getMemoryMXBean
    (1 to 5).foreach { _ =>
      System.gc(); Thread.sleep(50)
    }
    bean.getHeapMemoryUsage.getUsed
  }
}

@AuxCounters(AuxCounters.Type.EVENTS)
@State(Scope.Thread)
class RetainedCounters {
  var retainedKB: Long = 0
}

/** Heap retained while `consumers` conversions of the same directory are held simultaneously. */
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
class FileConverterRetainedBenchmark {

  @Benchmark
  def retainedFreshConverters(
      state: FileConverterState,
      counters: RetainedCounters
  ): Array[VirtualFile] = {
    val before = HeapMeter.usedHeap()
    val held =
      Array.tabulate(state.consumers)(_ => state.newConverter.toVirtualFile(state.classesDir))
    counters.retainedKB = (HeapMeter.usedHeap() - before) / 1024
    held
  }

  @Benchmark
  def retainedSharedConverter(
      state: FileConverterState,
      counters: RetainedCounters
  ): Array[VirtualFile] = {
    val before = HeapMeter.usedHeap()
    val converter = state.newConverter
    val held = Array.tabulate(state.consumers)(_ => converter.toVirtualFile(state.classesDir))
    counters.retainedKB = (HeapMeter.usedHeap() - before) / 1024
    held
  }
}
