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

import net.openhft.affinity.AffinityLock
import org.openjdk.jmh.annotations._

import java.io.File
import sbt.inc.{ CompilerSetup, ProjectSetup }
import sbt.internal.inc.BridgeProviderSpecification
import sbt.util.Logger
import xsbt.ZincBenchmark.CompilationInfo
import xsbti.compile.IncOptions

@State(Scope.Benchmark)
class BenchmarkBase extends BridgeProviderSpecification {
  /* Necessary data to run a benchmark. */
  @Param(Array("")) var _tempDir: String = _
  var _project: BenchmarkProject = _
  var _subprojectToRun: String = _
  @Param(Array("true"))
  var zincEnabled: Boolean = _

  /* Data filled in by the benchmark setup. */
  var _dir: File = _
  var _setup: ProjectSetup = _
  var _compilerSetup: CompilerSetup = _
  var _subprojectsSetup: List[ProjectSetup] = _

  /* Java thread affinity (install JNA to run this benchmark). */
  var _lock: AffinityLock = _

  @Setup(Level.Trial)
  def setUpCompilerRuns(): Unit = {
    _lock = AffinityLock.acquireLock()

    assert(_project != null, "_project is null, set it.")
    assert(_subprojectToRun != null, "_subprojectToRun is null, set it.")

    _dir = (new File(_tempDir)).getCanonicalFile
    assert(_dir.exists(), s"Unexpected inexistent directory ${_tempDir}")

    val compiler = new ZincBenchmark(_project, zincEnabled = this.zincEnabled)
    _subprojectsSetup = compiler.readSetup(new File(_dir, _project.repo)).getOrCrash
    assert(_subprojectsSetup.nonEmpty)

    val id = CompilationInfo.createIdentifierFor(_subprojectToRun, _project)
    _setup = _subprojectsSetup
      .find(p => p.subproject == id)
      .getOrElse(sys.error(s"No subproject ${_subprojectToRun} found."))

    val noLogger = Logger.Null
    val scalaVersion = ZincBenchmark.scalaVersion
    val bridge = getCompilerBridge(_dir.toPath, noLogger, scalaVersion)
    val si = scalaInstance(scalaVersion, _dir.toPath, noLogger)
    val options = IncOptions.of()
    _compilerSetup = _setup.createCompiler(scalaVersion, si, bridge, options, log)
    printCompilationDetails()
  }

  private def printCompilationDetails() = {
    val argsFile = new File(_tempDir, "compiler.args")
    val argsFileContents: String = {
      val cpArgs =
        if (_setup.classPath.isEmpty) Nil
        else "-cp" :: _setup.classPath.mkString(File.pathSeparator) :: Nil
      val allArgs: List[String] = cpArgs
      // ::: info.scalacOptions.toList ::: info.sources
      allArgs.mkString("\n")
    }
    sbt.io.IO.write(argsFile, argsFileContents)
    val shortSha = _project.hash.take(7)
    println(
      s"\nCompiling {${_project.repo}@${shortSha}}/${_subprojectToRun} using: @${argsFile.getAbsolutePath}"
    )
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit = {
    _lock.release()
    // Remove the directory where all the class files have been compiled
    _setup.sources.keys.toList map { x =>
      sbt.io.IO.delete(x.toFile)
    }
    ()
  }

  protected def action(): Unit = {
    _compilerSetup.doCompile()
    ()
  }
}
