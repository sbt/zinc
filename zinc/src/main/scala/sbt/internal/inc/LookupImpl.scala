package sbt.internal.inc

import java.io.File

import sbt.util.Logger._
import xsbti.Maybe
import xsbti.compile.CompileAnalysis

class LookupImpl(compileConfiguration: CompilerClasspathConfig) extends Lookup {
  import sbt.util.InterfaceUtil.m2o
  private val lookup = compileConfiguration.perClasspathEntryLookup
  private val entry = MixedAnalyzingCompiler.classPathLookup(compileConfiguration)
  override def lookupOnClasspath(binaryClassName: String): Option[File] =
    entry(binaryClassName)
  override def lookupAnalysis(classFile: File): Option[CompileAnalysis] =
    m2o(lookup.lookupAnalysis(classFile))
  override def lookupAnalysis(binaryDependency: File, binaryClassName: String): Option[CompileAnalysis] =
    m2o(lookup.lookupAnalysis(binaryDependency, binaryClassName))
  override def lookupAnalysis(binaryClassName: String): Option[CompileAnalysis] =
    m2o(lookup.lookupAnalysis(binaryClassName))

  lazy val externalLookup = Option(compileConfiguration.incOptions.externalHooks())
    .flatMap(ext => Option(ext.externalLookup()))
    .collect {
      case externalLookup: ExternalLookup => externalLookup
    }

  override def changedSources(previousAnalysis: Analysis): Option[Changes[File]] =
    externalLookup.flatMap(_.changedSources(previousAnalysis))

  override def changedBinaries(previousAnalysis: Analysis): Option[Set[File]] =
    externalLookup.flatMap(_.changedBinaries(previousAnalysis))

  override def removedProducts(previousAnalysis: Analysis): Option[Set[File]] =
    externalLookup.flatMap(_.removedProducts(previousAnalysis))

  override def shouldDoIncrementalCompilation(changedClasses: Set[String], analysis: Analysis): Boolean =
    externalLookup.forall(_.shouldDoIncrementalCompilation(changedClasses, analysis))
}
