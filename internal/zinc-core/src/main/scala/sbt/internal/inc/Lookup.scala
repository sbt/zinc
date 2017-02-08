/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File

import xsbti.compile.{ CompileAnalysis, ExternalHooks, FileHash }

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

trait ExternalLookup extends ExternalHooks.Lookup {
  /**
   * Used to provide information from external tools into sbt (e.g. IDEs)
   *
   * @param previousAnalysis
   * @return None if is unable to determine what was changed, changes otherwise
   */
  def changedSources(previousAnalysis: Analysis): Option[Changes[File]]

  /**
   * Used to provide information from external tools into sbt (e.g. IDEs)
   *
   * @param previousAnalysis
   * @return None if is unable to determine what was changed, changes otherwise
   */
  def changedBinaries(previousAnalysis: Analysis): Option[Set[File]]

  /**
   * Used to provide information from external tools into sbt (e.g. IDEs)
   *
   * @param previousAnalysis
   * @return None if is unable to determine what was changed, changes otherwise
   */
  def removedProducts(previousAnalysis: Analysis): Option[Set[File]]

  /**
   * Used to provide information from external tools into sbt (e.g. IDEs)
   * @return API changes
   */
  def shouldDoIncrementalCompilation(changedClasses: Set[String], analysis: Analysis): Boolean
}