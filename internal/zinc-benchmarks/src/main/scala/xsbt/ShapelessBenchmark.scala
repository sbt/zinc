package xsbt

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class BenchmarkBase {
  var _project: BenchmarkProject = _
  var _projectsSetup: List[ProjectSetup] = _

  @Setup(Level.Trial)
  def setUpCompilerRuns(): Unit = {
    val compiler = new ZincBenchmark(_project)
    _projectsSetup = compiler.prepare.getOrCrash
  }

  protected def compile(): Unit = {
    // TODO: Tweak to benchmark the rest of the projects as well
    _projectsSetup.head.compile
  }
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 16, jvmArgs = Array("-XX:CICompilerCount=2"))
class ShapelessBenchmark extends BenchmarkBase {
  _project = BenchmarkProjects.Shapeless
  @Benchmark
  override def compile(): Unit = {
    super.compile()
  }
}
