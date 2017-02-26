/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File

import sbt.util.Logger._
import xsbti.compile.{ CompileAnalysis, MiniSetup, FileHash }

class LookupImpl(compileConfiguration: CompileConfiguration, previousSetup: Option[MiniSetup]) extends Lookup {
  private val classpath: Vector[File] = compileConfiguration.classpath.toVector
  private val classpathHash: Vector[FileHash] = compileConfiguration.currentSetup.options.classpathHash.toVector

  lazy val analyses: Vector[Analysis] =
    classpath flatMap { entry =>
      m2o(compileConfiguration.perClasspathEntryLookup.analysis(entry)) map
        { case a: Analysis => a }
    }
  lazy val previousClasspathHash: Vector[FileHash] =
    previousSetup match {
      case Some(x) => x.options.classpathHash.toVector
      case _       => Vector()
    }
  def changedClasspathHash: Option[Vector[FileHash]] =
    if (classpathHash == previousClasspathHash) None
    else Some(classpathHash)

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
