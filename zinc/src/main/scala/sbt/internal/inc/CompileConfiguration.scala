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
  override val classpath: Seq[File],
  val previousAnalysis: CompileAnalysis,
  val previousSetup: Option[MiniSetup],
  override val currentSetup: MiniSetup,
  val progress: Option[CompileProgress],
  override val perClasspathEntryLookup: PerClasspathEntryLookup,
  val reporter: Reporter,
  override val compiler: xsbti.compile.ScalaCompiler,
  val javac: xsbti.compile.JavaCompiler,
  val cache: GlobalsCache,
  override val incOptions: IncOptions
) extends CompilerClasspathConfig

trait CompilerClasspathConfig {
  val currentSetup: MiniSetup

  def classpath: Seq[File]

  def perClasspathEntryLookup: PerClasspathEntryLookup

  def compiler: xsbti.compile.ScalaCompiler

  def incOptions: IncOptions
}
