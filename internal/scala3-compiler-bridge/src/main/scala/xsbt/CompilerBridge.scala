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
import xsbti.{ AnalysisCallback, Logger, Reporter, VirtualFile }
import xsbti.compile.{ DependencyChanges, Output, CompileProgress }
import dotty.tools.dotc.util.SourceFile

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
    val sourceFiles = sources.view
      .sortBy(_.id)
      .map(AbstractZincFile(_))
      .map(SourceFile(_, scala.io.Codec.UTF8))
      .toList
    cached.run(sourceFiles, callback, log, delegate)
  }
}
