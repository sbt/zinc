package xsbt

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

class ShapelessBenchmark extends BenchmarkBase {
  _project = BenchmarkProjects.Shapeless
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 10, jvmArgs = Array("-XX:CICompilerCount=2"))
class ColdShapelessBenchmark extends ShapelessBenchmark {
  _subprojectToRun = _project.subprojects.head // CoreJVM
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
class WarmShapelessBenchmark extends ShapelessBenchmark {
  _subprojectToRun = _project.subprojects.head // CoreJVM
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
class HotShapelessBenchmark extends ShapelessBenchmark {
  _subprojectToRun = _project.subprojects.head // CoreJVM
  @Benchmark
  override def compile(): Unit = {
    super.compile()
  }
}
