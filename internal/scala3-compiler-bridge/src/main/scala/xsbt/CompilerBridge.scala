/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbt

import dotty.tools.dotc.Main
import dotty.tools.dotc.config.Properties
import dotty.tools.dotc.core.Contexts.ContextBase
import xsbt.Log.debug
import xsbti.{ AnalysisCallback, Logger, PathBasedFile, Problem, Reporter, VirtualFile }
import xsbti.compile._

/**
 * This is the entry point for the compiler bridge (implementation of CompilerInterface2)
 */
final class CompilerBridge extends xsbti.compile.CompilerInterface2 {
  override def run(
      sources: Array[VirtualFile],
      changes: DependencyChanges,
      options: Array[String],
      output: Output,
      callback: AnalysisCallback,
      delegate: Reporter,
      progress: CompileProgress,
      log: Logger
  ): Unit = {
    val cached = new CachedCompilerImpl(options, output)
    cached.run(sources, callback, log, delegate)
  }
}

private final class CachedCompilerImpl(
    args: Array[String],
    output: Output
) {

  private val outputArgs = output match {
    case output: SingleOutput =>
      Array("-d", output.getOutputDirectoryAsPath.toString)
    case _ =>
      throw new IllegalArgumentException(
        s"output should be a SingleOutput, was a ${output.getClass.getName}"
      );
  }

  private def commandArguments(sources: Array[VirtualFile]): Array[String] = {
    val files = sources.map {
      case pathBased: PathBasedFile => pathBased
      case virtualFile =>
        throw new IllegalArgumentException(
          s"source should be a PathBasedFile, was ${virtualFile.getClass.getName}"
        )
    }
    outputArgs ++ args ++ files.map(_.toPath.toString)
  }

  private def infoOnCachedCompiler(compilerId: String): String =
    s"[zinc] Running cached compiler $compilerId for Scala compiler ${Properties.versionString}"

  private def prettyPrintCompilationArguments(args: Array[String]) =
    args.mkString("[zinc] The Scala compiler is invoked with:\n\t", "\n\t", "")

  def run(
      sources: Array[VirtualFile],
      callback: AnalysisCallback,
      log: Logger,
      delegate: Reporter,
  ): Unit = synchronized {
    debug(log, infoOnCachedCompiler(hashCode().toLong.toHexString))
    debug(log, prettyPrintCompilationArguments(args))
    val dreporter = new DelegatingReporter(delegate)
    try {
      val ctx = new ContextBase().initialCtx.fresh
        .setSbtCallback(callback)
        .setReporter(dreporter)
      val reporter = Main.process(commandArguments(sources), ctx)
      if (reporter.hasErrors)
        throw new InterfaceCompileFailed(args, Array(), "Compilation failed")
    } finally {
      dreporter.dropDelegate()
    }
  }
}

class InterfaceCompileFailed(
    val arguments: Array[String],
    val problems: Array[Problem],
    override val toString: String
) extends xsbti.CompileFailed
