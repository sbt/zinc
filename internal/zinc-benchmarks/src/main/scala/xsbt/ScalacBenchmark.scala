/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
@Fork(value = 10, jvmArgs = Array("-XX:CICompilerCount=2"))
class ColdScalacBenchmark extends ScalacBenchmark {
  _subprojectToRun = _project.subprojects.head
  @Benchmark
  override def compile(): Unit = {
    super.compile()
  }
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
class WarmScalacBenchmark extends ScalacBenchmark {
  _subprojectToRun = _project.subprojects.head
  @Benchmark
  override def compile(): Unit = {
    super.compile()
  }
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
class HotScalacBenchmark extends ScalacBenchmark {
  _subprojectToRun = _project.subprojects.head
  @Benchmark
  override def compile(): Unit = {
    super.compile()
  }
}
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
class HotScalacApiExtractBenchmark extends ScalacBenchmark {
  _subprojectToRun = _project.subprojects.head
  var _body: () => Unit = null
  @Setup(Level.Trial)
  def runCompiler(): Unit = {
    _body = _setup.apiExtract()
  }
  @Benchmark
  override def compile(): Unit = {
    _body.apply()
  }
}
