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

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

class ScalacBenchmark extends BenchmarkBase {
  _project = BenchmarkProjects.Scalac
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 5, jvmArgs = Array("-XX:CICompilerCount=2"))
class ColdScalacBenchmark extends ScalacBenchmark {
  _subprojectToRun = _project.subprojects.head
  @Benchmark
  override def action(): Unit = {
    super.action()
  }
}

/*
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
class WarmScalacBenchmark extends ScalacBenchmark {
  _subprojectToRun = _project.subprojects.head
  @Benchmark
  override def action(): Unit = {
    super.action()
  }
}
 */

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
class HotScalacBenchmark extends ScalacBenchmark {
  _subprojectToRun = _project.subprojects.head
  @Benchmark
  override def action(): Unit = {
    super.action()
  }
}
