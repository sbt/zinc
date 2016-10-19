package sbt.internal.inc

import java.io.File

import sbt.util.Logger._
import xsbti.Maybe
import xsbti.compile.{ CompileAnalysis, MiniSetup }

class LookupImpl(compileConfiguration: CompileConfiguration, previousSetup: Option[MiniSetup]) extends Lookup {
  private val classpath: Vector[File] = compileConfiguration.classpath.toVector
  lazy val analyses: Vector[Analysis] =
    classpath flatMap { entry =>
      m2o(compileConfiguration.perClasspathEntryLookup.analysis(entry)) map
        { case a: Analysis => a }
    }
  lazy val previousClasspath: Vector[File] =
    previousSetup match {
      case Some(x) => x.options.classpath.toVector
      case _       => Vector()
    }
  def changedClasspath: Option[Vector[File]] =
    if (classpath == previousClasspath) None
    else Some(classpath)

  private val entry = MixedAnalyzingCompiler.classPathLookup(compileConfiguration)

  override def lookupAnalysis(binaryClassName: String): Option[CompileAnalysis] =
    analyses collectFirst {
      case a if a.relations.productClassName._2s contains binaryClassName => a
    }
  override def lookupOnClasspath(binaryClassName: String): Option[File] =
    entry(binaryClassName)

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
