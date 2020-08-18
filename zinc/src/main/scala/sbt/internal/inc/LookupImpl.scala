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

package sbt.internal.inc

import java.util.Optional

import xsbti.api.AnalyzedClass
import xsbti.compile.{ Changes, CompileAnalysis, FileHash, MiniSetup }
import xsbti.{ VirtualFile, VirtualFileRef }

class LookupImpl(compileConfiguration: CompileConfiguration, previousSetup: Option[MiniSetup])
    extends Lookup {
  private val classpath: Vector[VirtualFile] = compileConfiguration.classpath.toVector
  private val classpathHash: Vector[FileHash] =
    compileConfiguration.currentSetup.options.classpathHash.toVector

  import sbt.internal.inc.JavaInterfaceUtil.EnrichOptional
  lazy val analyses: Vector[Analysis] = {
    classpath flatMap { entry =>
      compileConfiguration.perClasspathEntryLookup.analysis(entry).toOption.map {
        case a: Analysis => a
      }
    }
  }

  lazy val previousClasspathHash: Vector[FileHash] = {
    previousSetup match {
      case Some(x) => x.options.classpathHash.toVector
      case _       => Vector()
    }
  }

  def changedClasspathHash: Option[Vector[FileHash]] =
    if (classpathHash == previousClasspathHash) None
    else Some(classpathHash)

  private val entry = MixedAnalyzingCompiler.classPathLookup(compileConfiguration)

  override def lookupAnalysis(binaryClassName: String): Option[CompileAnalysis] =
    analyses.find(_.relations.productClassName._2s.contains(binaryClassName))

  override def lookupOnClasspath(binaryClassName: String): Option[VirtualFileRef] =
    entry(binaryClassName)
      .map(compileConfiguration.converter.toVirtualFile(_))

  lazy val externalLookup = Option(compileConfiguration.incOptions.externalHooks())
    .flatMap(ext => ext.getExternalLookup().toOption)
    .collect { case externalLookup: ExternalLookup => externalLookup }

  override def lookupAnalyzedClass(binaryClassName: String): Option[AnalyzedClass] = {
    externalLookup match { // not flatMap so that external lookup can fast-track returning None
      case Some(externalLookup) =>
        externalLookup.lookupAnalyzedClass(binaryClassName) match {
          case Some(api) if api.provenance.isEmpty => // found but w/o provenance, so go slow route
            super.lookupAnalyzedClass(binaryClassName)
          case x => x // fast-track success: either found w/ provenance or not found at all
        }
      case _ => super.lookupAnalyzedClass(binaryClassName)
    }
  }

  override def changedSources(previousAnalysis: CompileAnalysis): Option[Changes[VirtualFileRef]] =
    externalLookup.flatMap(_.changedSources(previousAnalysis))

  override def changedBinaries(previousAnalysis: CompileAnalysis): Option[Set[VirtualFileRef]] =
    externalLookup.flatMap(_.changedBinaries(previousAnalysis))

  override def removedProducts(
      previousAnalysis: CompileAnalysis
  ): Option[Set[VirtualFileRef]] =
    externalLookup.flatMap(_.removedProducts(previousAnalysis))

  override def shouldDoIncrementalCompilation(
      changedClasses: Set[String],
      analysis: CompileAnalysis
  ): Boolean =
    externalLookup.forall(_.shouldDoIncrementalCompilation(changedClasses, analysis))

  override def shouldDoEarlyOutput(analysis: CompileAnalysis): Boolean = {
    externalLookup match {
      case Some(externalLookup) => externalLookup.shouldDoEarlyOutput(analysis)
      case None                 => super.shouldDoEarlyOutput(analysis)
    }
  }

  override def hashClasspath(classpath: Array[VirtualFile]): Optional[Array[FileHash]] =
    externalLookup.map(_.hashClasspath(classpath)).getOrElse(Optional.empty())
}
