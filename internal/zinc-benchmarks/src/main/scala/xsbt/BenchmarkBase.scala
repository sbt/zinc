package xsbt

import net.openhft.affinity.AffinityLock
import org.openjdk.jmh.annotations._
import java.io.File

import xsbt.ZincBenchmark.CompilationInfo

@State(Scope.Benchmark)
class BenchmarkBase {
  /* Necessary data to run a benchmark. */
  @Param(Array("")) var _tempDir: String = _
  var _project: BenchmarkProject = _
  var _subprojectToRun: String = _

  /* Data filled in by the benchmark setup. */
  var _dir: File = _
  var _message: String = _
  var _setup: ProjectSetup = _
  var _subprojectsSetup: List[ProjectSetup] = _

  /* Java thread affinity (install JNA to run this benchmark). */
  var _lock: AffinityLock = _

  @Setup(Level.Trial)
  def setUpCompilerRuns(): Unit = {
    _lock = AffinityLock.acquireLock()

    assert(_project != null, "_project is null, set it.")
    assert(_subprojectToRun != null, "_subprojectToRun is null, set it.")

    _dir = new File(_tempDir)
    assert(_dir.exists(), s"Unexpected inexistent directory ${_tempDir}")

    val compiler = new ZincBenchmark(_project)
    _subprojectsSetup = compiler.readSetup(_dir).getOrCrash
    assert(_subprojectsSetup.nonEmpty)

    val id = CompilationInfo.createIdentifierFor(_subprojectToRun, _project)
    _setup = _subprojectsSetup
      .find(p => p.subproject == id)
      .getOrElse(sys.error(s"No subproject ${_subprojectToRun} found."))
    _message = {
      val info = _setup.compilationInfo
      s"""Compiling with:
         |
         |> Classpath: ${info.classpath}
         |
         |> Scalac options: ${info.scalacOptions.mkString(" ")}
         |
         |> Scala sources: ${info.sources.mkString(" ")}
      """.stripMargin
    }
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit = {
    _lock.release()
    // Remove the directory where all the class files have been compiled
    sbt.io.IO.delete(_setup.at)
  }

  protected def compile(): Unit = {
    println(_message.head)
    _setup.compile()
  }
}
