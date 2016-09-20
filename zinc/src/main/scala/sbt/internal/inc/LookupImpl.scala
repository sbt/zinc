package sbt.internal.inc

import java.io.File

import sbt.util.Logger._
import xsbti.Maybe
import xsbti.compile.CompileAnalysis

class LookupImpl(compileConfiguration: CompileConfiguration) extends Lookup {
  private val entry = MixedAnalyzingCompiler.classPathLookup(compileConfiguration)

  override def lookupOnClasspath(binaryClassName: String): Option[File] =
    entry(binaryClassName)

  override def lookupAnalysis(classFile: File): Option[CompileAnalysis] =
    m2o(compileConfiguration.perClasspathEntryLookup.analysis(classFile))

  override def lookupAnalysis(binaryDependency: File, binaryClassName: String): Option[CompileAnalysis] = {
    lookupOnClasspath(binaryClassName) flatMap { defines =>
      if (binaryDependency != Locate.resolve(defines, binaryClassName))
        None
      else
        lookupAnalysis(defines)
    }
  }

  lazy val externalLookup = Option(compileConfiguration.incOptions.externalHooks())
    .flatMap(ext => Option(ext.externalLookup()))
    .collect {
      case externalLookup: ExternalLookup => externalLookup
    }

  override def lookupAnalysis(binaryClassName: String): Option[CompileAnalysis] =
    lookupOnClasspath(binaryClassName).flatMap(lookupAnalysis)

  override def changedSources(previousAnalysis: Analysis): Option[Changes[File]] =
    externalLookup.flatMap(_.changedSources(previousAnalysis))

  override def changedBinaries(previousAnalysis: Analysis): Option[Set[File]] =
    externalLookup.flatMap(_.changedBinaries(previousAnalysis))

  override def removedProducts(previousAnalysis: Analysis): Option[Set[File]] =
    externalLookup.flatMap(_.removedProducts(previousAnalysis))

  override def shouldDoIncrementalCompilation(changedClasses: Set[String], analysis: Analysis): Boolean =
    externalLookup.forall(_.shouldDoIncrementalCompilation(changedClasses, analysis))
}
