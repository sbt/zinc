package sbt.internal.inc

import java.io.File

import sbt.util.Logger._
import xsbti.Maybe
import xsbti.compile.CompileAnalysis

class LookupImpl(compileConfiguration: CompilerClasspathConfig) extends Lookup {
  private val (classpath, entry) = MixedAnalyzingCompiler.searchClasspathAndLookup(compileConfiguration)

  /** External dependencies (Analysis) ordered in the same way as on classpath */
  private val externalDependenciesCp: Stream[Analysis] = classpath.toStream.flatMap(analysisForCpEntry)

  private lazy val externalLookup = Option(compileConfiguration.incOptions.externalHooks())
    .flatMap(ext => Option(ext.externalLookup()))
    .collect {
      case externalLookup: ExternalLookup => externalLookup
    }

  override def lookupOnClasspath(binaryClassName: String): Option[File] =
    entry(binaryClassName)

  override def lookupAnalysis(classFile: File): Option[CompileAnalysis] =
    externalDependenciesCp.find(definesProduct(classFile))

  override def lookupAnalysis(binaryDependency: File, binaryClassName: String): Option[CompileAnalysis] =
    for {
      classpathEntry <- lookupOnClasspath(binaryClassName)
      analysis <- analysisForCpEntry(classpathEntry)
      if definesProduct(binaryDependency)(analysis)
    } yield analysis

  override def lookupAnalysis(binaryClassName: String): Option[CompileAnalysis] =
    if (compileConfiguration.incOptions.analysisOnlyExtDepLookup())
      externalDependenciesCp.find(_.relations.binaryClassName.reverseMap.contains(binaryClassName))
    else lookupOnClasspath(binaryClassName).flatMap(analysisForCpEntry)

  override def changedSources(previousAnalysis: Analysis): Option[Changes[File]] =
    externalLookup.flatMap(_.changedSources(previousAnalysis))

  override def changedBinaries(previousAnalysis: Analysis): Option[Set[File]] =
    externalLookup.flatMap(_.changedBinaries(previousAnalysis))

  override def removedProducts(previousAnalysis: Analysis): Option[Set[File]] =
    externalLookup.flatMap(_.removedProducts(previousAnalysis))

  private def definesProduct(product: File)(a: Analysis): Boolean =
    a.stamps.products.contains(product)

  private def analysisForCpEntry(cpEntry: File): Option[Analysis] =
    m2o(compileConfiguration.perClasspathEntryLookup.analysis(cpEntry).asInstanceOf[Maybe[Analysis]])

  override def shouldDoIncrementalCompilation(changedClasses: Set[String], analysis: Analysis): Boolean =
    externalLookup.forall(_.shouldDoIncrementalCompilation(changedClasses, analysis))
}

