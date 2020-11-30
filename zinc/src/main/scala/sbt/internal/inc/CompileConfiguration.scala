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

package sbt
package internal
package inc

import xsbti.{ FileConverter, Reporter, VirtualFile }
import xsbti.compile.{
  AnalysisStore => XAnalysisStore,
  CompileAnalysis,
  CompileProgress,
  GlobalsCache,
  IncOptions,
  MiniSetup,
  Output,
  PerClasspathEntryLookup
}
import xsbti.compile.analysis.ReadStamps

/**
 * Configuration used for running an analyzing compiler (a compiler which can extract dependencies between source files and JARs).
 *
 * @param sources
 * @param converter
 * @param classpath
 * @param previousAnalysis
 * @param previousSetup
 * @param currentSetup
 * @param progress
 * @param perClasspathEntryLookup
 * @param reporter
 * @param compiler
 * @param javac
 * @param cache
 * @param incOptions
 */
final class CompileConfiguration(
    val sources: Seq[VirtualFile],
    val converter: FileConverter,
    val classpath: Seq[VirtualFile],
    val previousAnalysis: CompileAnalysis,
    val previousSetup: Option[MiniSetup],
    val currentSetup: MiniSetup,
    val progress: Option[CompileProgress],
    val perClasspathEntryLookup: PerClasspathEntryLookup,
    val reporter: Reporter,
    val compiler: xsbti.compile.ScalaCompiler,
    val javac: xsbti.compile.JavaCompiler,
    val cache: GlobalsCache,
    val incOptions: IncOptions,
    val outputJarContent: JarUtils.OutputJarContent,
    val earlyOutput: Option[Output],
    val earlyAnalysisStore: Option[XAnalysisStore],
    val stampReader: ReadStamps,
)
