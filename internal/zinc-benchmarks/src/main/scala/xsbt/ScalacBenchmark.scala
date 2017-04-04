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
