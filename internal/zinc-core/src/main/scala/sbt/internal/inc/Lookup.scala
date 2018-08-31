/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File
import java.util
import java.util.Optional

import xsbti.compile.{ Changes, CompileAnalysis, ExternalHooks, FileHash }

/**
 * A trait that encapsulates looking up elements on a classpath and looking up
 * an external (for another subproject) Analysis instance.
 */
trait Lookup extends ExternalLookup {

  /**
   * Returns the current classpath if the classpath has changed from the last compilation.
   */
  def changedClasspathHash: Option[Vector[FileHash]]

  def analyses: Vector[CompileAnalysis]

  /**
   * Lookup an element on the classpath corresponding to a given binary class name.
   * If found class file is stored in a jar file, the jar file is returned.
   *
   * @param binaryClassName
   * @return
   */
  def lookupOnClasspath(binaryClassName: String): Option[File]

  /**
   * Return an Analysis instance that has the given binary class name registered as a product.
   *
   * @param binaryClassName
   * @return
   */
  def lookupAnalysis(binaryClassName: String): Option[CompileAnalysis]
}

/**
 * Defines a hook interface that IDEs or build tools can mock to modify the way
 * Zinc invalidates the incremental compiler. These hooks operate at a high-level
 * of abstraction and only allow to modify the inputs of the initial change detection.
 */
trait ExternalLookup extends ExternalHooks.Lookup {
  import sbt.internal.inc.JavaInterfaceUtil.EnrichOption
  import scala.collection.JavaConverters._

  /**
   * Used to provide information from external tools into sbt (e.g. IDEs)
   *
   * @param previousAnalysis
   * @return None if is unable to determine what was changed, changes otherwise
   */
  def changedSources(previousAnalysis: CompileAnalysis): Option[Changes[File]]
  override def getChangedSources(previousAnalysis: CompileAnalysis): Optional[Changes[File]] =
    changedSources(previousAnalysis).toOptional

  /**
   * Used to provide information from external tools into sbt (e.g. IDEs)
   *
   * @param previousAnalysis
   * @return None if is unable to determine what was changed, changes otherwise
   */
  def changedBinaries(previousAnalysis: CompileAnalysis): Option[Set[File]]
  override def getChangedBinaries(previousAnalysis: CompileAnalysis): Optional[util.Set[File]] =
    changedBinaries(previousAnalysis).map(_.asJava).toOptional

  /**
   * Used to provide information from external tools into sbt (e.g. IDEs)
   *
   * @param previousAnalysis
   * @return None if is unable to determine what was changed, changes otherwise
   */
  def removedProducts(previousAnalysis: CompileAnalysis): Option[Set[File]]
  override def getRemovedProducts(previousAnalysis: CompileAnalysis): Optional[util.Set[File]] =
    removedProducts(previousAnalysis).map(_.asJava).toOptional

  /**
   * Used to provide information from external tools into sbt (e.g. IDEs)
   * @return API changes
   */
  def shouldDoIncrementalCompilation(changedClasses: Set[String],
                                     analysis: CompileAnalysis): Boolean
  override def shouldDoIncrementalCompilation(changedClasses: util.Set[String],
                                              previousAnalysis: CompileAnalysis): Boolean = {
    import scala.collection.JavaConverters._
    shouldDoIncrementalCompilation(changedClasses.iterator().asScala.toSet, previousAnalysis)
  }
}

trait NoopExternalLookup extends ExternalLookup {
  override def changedSources(previous: CompileAnalysis): Option[Changes[File]] = None
  override def changedBinaries(previous: CompileAnalysis): Option[Set[File]] = None
  override def removedProducts(previous: CompileAnalysis): Option[Set[File]] = None
  override def shouldDoIncrementalCompilation(changedClasses: Set[String],
                                              analysis: CompileAnalysis): Boolean = true
  override def hashClasspath(classpath: Array[File]): Optional[Array[FileHash]] = Optional.empty()
}
