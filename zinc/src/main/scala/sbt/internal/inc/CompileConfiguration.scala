package sbt
package internal
package inc

import java.io.File

import xsbti.Reporter
import xsbti.compile.{ GlobalsCache, CompileProgress, IncOptions, MiniSetup, CompileAnalysis, PerClasspathEntryLookup }

/**
 * Configuration used for running an analyzing compiler (a compiler which can extract dependencies between source files and JARs).
 *
 * @param sources
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
  val sources: Seq[File],
  val classpath: Seq[File],
  val previousAnalysis: CompileAnalysis,
  val previousSetup: Option[MiniSetup],
  val currentSetup: MiniSetup,
  val progress: Option[CompileProgress],
  val perClasspathEntryLookup: PerClasspathEntryLookup,
  val reporter: Reporter,
  val compiler: AnalyzingCompiler,
  val javac: xsbti.compile.JavaCompiler,
  val cache: GlobalsCache,
  val incOptions: IncOptions
)
