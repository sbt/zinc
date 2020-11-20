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

import dotty.tools.dotc.Driver
import dotty.tools.dotc.config.{Properties, Settings}
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.util.SourceFile
import xsbt.Log.debug
import xsbti.{ AnalysisCallback, Logger, Problem, Reporter }
import xsbti.compile.{ Output, SingleOutput }

private final class CachedCompilerImpl(
    options: Array[String],
    output: Output
) extends Driver {
  private val args = output match {
    case output: SingleOutput =>
      options ++ Seq("-d", output.getOutputDirectoryAsPath.toString)
    case _ =>
      throw new IllegalArgumentException(
        s"output should be a SingleOutput, was a ${output.getClass.getName}"
      )
  }

  /**
   * `sourcesRequired` is set to false because `args` do not contain the sources
   * The sources are passed programmatically to the Scala 3 compiler
   */
  override def sourcesRequired: Boolean = false

  def run(
      sources: List[SourceFile],
      callback: AnalysisCallback,
      log: Logger,
      delegate: Reporter,
  ): Unit = synchronized {
    val reporter = new DelegatingReporter(delegate)

    try {
      debug(log, infoOnCachedCompiler)

      val initialCtx = initCtx.fresh
        .setReporter(reporter)
        .setSbtCallback(callback)

      given Context = setup(args,initialCtx)(1)

      if (shouldStopWithInfo) {
        throw new InterfaceCompileFailed(options, Array(), StopInfoError)
      }

      val isCancelled = 
        if (!delegate.hasErrors) {
          debug(log, prettyPrintCompilationArguments)
          val run = newCompiler.newRun
          run.compileSources(sources)
          // allConditionalWarning is not available in Scala 3
          // processUnreportedWarnings(run)
          delegate.problems.foreach(
            p => callback.problem(p.category, p.position, p.message, p.severity, true)
          )

          run.isCancelled
        } else false

      delegate.printSummary()
      
      if (delegate.hasErrors) {
        debug(log, "Compilation failed (CompilerInterface)")
        throw new InterfaceCompileFailed(args, delegate.problems, "Compilation failed")
      }

      // the case where we cancelled compilation _after_ some compilation errors got reported
      // will be handled by line above so errors still will be reported properly just potentially not
      // all of them (because we cancelled the compilation)
      if (isCancelled) {
        debug(log, "Compilation cancelled (CompilerInterface)")
        throw new InterfaceCompileCancelled(args, "Compilation has been cancelled")
      }
    }  finally {
      reporter.dropDelegate()
    }
  }

  private def shouldStopWithInfo(using ctx: Context) = {
    val settings = ctx.settings
    import settings._
    Set(help, Xhelp, Yhelp, showPlugins, XshowPhases).exists(_.value(using ctx))
  }

  private val StopInfoError: String = 
    "Compiler option supplied that disabled Zinc compilation."

  private def infoOnCachedCompiler: String = {
    val compilerId = hashCode().toLong.toHexString
    s"[zinc] Running cached compiler $compilerId for Scala compiler ${Properties.versionString}"
  }

  private def prettyPrintCompilationArguments =
    options.mkString("[zinc] The Scala compiler is invoked with:\n\t", "\n\t", "")
}

class InterfaceCompileFailed(
    val arguments: Array[String],
    val problems: Array[Problem],
    override val toString: String
) extends xsbti.CompileFailed

class InterfaceCompileCancelled(val arguments: Array[String], override val toString: String)
    extends xsbti.CompileCancelled
