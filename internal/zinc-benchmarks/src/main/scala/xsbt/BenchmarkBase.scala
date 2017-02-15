package xsbt

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
    val firstProject = _projectsSetup.head
    val info = firstProject.compilationInfo
    println(
      s"""Compiling with:
        |
        |> Classpath: ${info.classpath}
        |
        |> Scalac options: ${info.scalacOptions.mkString(" ")}
        |
        |> Scala sources: ${info.sources.mkString(" ")}
      """.stripMargin
    )
    firstProject.compile()
  }
}
