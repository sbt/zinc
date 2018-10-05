/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File

import sbt.util.{ Level, Logger }
import xsbti.compile.analysis.{ ReadStamps, Stamp => XStamp }
import xsbti.compile.{
  ClassFileManager => XClassFileManager,
  CompileAnalysis,
  DependencyChanges,
  IncOptions,
  Output
}

/**
 * Define helpers to run incremental compilation algorithm with name hashing.
 */
object Incremental {
  class PrefixingLogger(val prefix: String)(orig: Logger) extends Logger {
    def trace(t: => Throwable): Unit = orig.trace(t)
    def success(message: => String): Unit = orig.success(message)
    def log(level: Level.Value, message: => String): Unit = level match {
      case Level.Debug => orig.log(level, message.replaceAll("(?m)^", prefix))
      case _           => orig.log(level, message)
    }
  }

  /**
   * Runs the incremental compiler algorithm.
   *
   * @param sources   The sources to compile
   * @param lookup
   *              An instance of the `Lookup` that implements looking up both classpath elements
   *              and Analysis object instances by a binary class name.
   * @param previous0 The previous dependency Analysis (or an empty one).
   * @param current  A mechanism for generating stamps (timestamps, hashes, etc).
   * @param compile  The function which can run one level of compile.
   * @param callbackBuilder The builder that builds callback where we report dependency issues.
   * @param log  The log where we write debugging information
   * @param options  Incremental compilation options
   * @param outputJarContent Object that holds cached content of output jar
   * @param profiler An implementation of an invalidation profiler, empty by default.
   * @param equivS  The means of testing whether two "Stamps" are the same.
   * @return
   *         A flag of whether or not compilation completed successfully, and the resulting dependency analysis object.
   */
  def compile(
      sources: Set[File],
      lookup: Lookup,
      previous0: CompileAnalysis,
      current: ReadStamps,
      compile: (Set[File], DependencyChanges, xsbti.AnalysisCallback, XClassFileManager) => Unit,
      callbackBuilder: AnalysisCallback.Builder,
      log: sbt.util.Logger,
      options: IncOptions,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      profiler: InvalidationProfiler = InvalidationProfiler.empty
  )(implicit equivS: Equiv[XStamp]): (Boolean, Analysis) = {
    val previous = previous0 match { case a: Analysis => a }
    val runProfiler = profiler.profileRun
    val incremental: IncrementalCommon = new IncrementalNameHashing(log, options, runProfiler)
    val initialChanges =
      incremental.detectInitialChanges(sources, previous, current, lookup, output)
    val binaryChanges = new DependencyChanges {
      val modifiedBinaries = initialChanges.binaryDeps.toArray
      val modifiedClasses = initialChanges.external.allModified.toArray
      def isEmpty = modifiedBinaries.isEmpty && modifiedClasses.isEmpty
    }
    val (initialInvClasses, initialInvSources) =
      incremental.invalidateInitial(previous.relations, initialChanges)
    if (initialInvClasses.nonEmpty || initialInvSources.nonEmpty)
      if (initialInvSources == sources) incremental.log.debug("All sources are invalidated.")
      else
        incremental.log.debug(
          "All initially invalidated classes: " + initialInvClasses + "\n" +
            "All initially invalidated sources:" + initialInvSources + "\n")
    val analysis = manageClassfiles(options, output, outputJarContent) { classfileManager =>
      incremental.cycle(initialInvClasses,
                        initialInvSources,
                        sources,
                        binaryChanges,
                        lookup,
                        previous,
                        doCompile(compile, callbackBuilder, classfileManager),
                        classfileManager,
                        1)
    }
    (initialInvClasses.nonEmpty || initialInvSources.nonEmpty, analysis)
  }

  /**
   * Compilation unit in each compile cycle.
   */
  def doCompile(
      compile: (Set[File], DependencyChanges, xsbti.AnalysisCallback, XClassFileManager) => Unit,
      callbackBuilder: AnalysisCallback.Builder,
      classFileManager: XClassFileManager
  )(srcs: Set[File], changes: DependencyChanges): Analysis = {
    // Note `ClassFileManager` is shared among multiple cycles in the same incremental compile run,
    // in order to rollback entirely if transaction fails. `AnalysisCallback` is used by each cycle
    // to report its own analysis individually.
    val callback = callbackBuilder.build()
    compile(srcs, changes, callback, classFileManager)
    callback.get
  }

  // the name of system property that was meant to enable debugging mode of incremental compiler but
  // it ended up being used just to enable debugging of relations. That's why if you migrate to new
  // API for configuring incremental compiler (IncOptions) it's enough to control value of `relationsDebug`
  // flag to achieve the same effect as using `incDebugProp`.
  @deprecated("Use `IncOptions.relationsDebug` flag to enable debugging of relations.", "0.13.2")
  val incDebugProp = "xsbt.inc.debug"

  private[inc] val apiDebugProp = "xsbt.api.debug"
  private[inc] def apiDebug(options: IncOptions): Boolean =
    options.apiDebug || java.lang.Boolean.getBoolean(apiDebugProp)

  private[sbt] def prune(invalidatedSrcs: Set[File],
                         previous0: CompileAnalysis,
                         output: Output,
                         outputJarContent: JarUtils.OutputJarContent): Analysis = {
    val previous = previous0.asInstanceOf[Analysis]
    IncrementalCommon.pruneClassFilesOfInvalidations(
      invalidatedSrcs,
      previous,
      ClassFileManager.deleteImmediately(output, outputJarContent))
  }

  private[this] def manageClassfiles[T](
      options: IncOptions,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent
  )(run: XClassFileManager => T): T = {
    val classfileManager = ClassFileManager.getClassFileManager(options, output, outputJarContent)
    val result = try run(classfileManager)
    catch {
      case e: Throwable =>
        classfileManager.complete(false)
        throw e
    }
    classfileManager.complete(true)
    result
  }

}
