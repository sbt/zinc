package sbt.internal.inc

import java.io.File

import xsbti.compile.{ CompileAnalysis, ExternalHooks }

/**
 * A trait that encapsulates looking up elements on a classpath and looking up
 * an external (for another subproject) Analysis instance.
 *
 * Multiple variants of `lookupAnalysis` exists because different parts of zinc
 * lookup an external Analysis a bit differently. In the future, only the
 * variant that takes binary class name as a parameter should be kept. This
 * variant can be implemented without an expensive classpath lookup.
 * See https://github.com/sbt/sbt/issues/2525
 */
trait Lookup extends ExternalLookup {
  /**
   * Lookup an element on the classpath corresponding to a given binary class name.
   * If found class file is stored in a jar file, the jar file is returned.
   *
   * @param binaryClassName
   * @return
   */
  def lookupOnClasspath(binaryClassName: String): Option[File]

  /**
   * Return an Analysis instance that has the given class file registered as a product.
   *
   * @param classFile
   * @return
   */
  def lookupAnalysis(classFile: File): Option[CompileAnalysis]

  /**
   * Return an Analysis instance that has the given class file registered as a product.
   * The class file has to correspond to the given binary class name.
   *
   * @param binaryDependency
   * @param binaryClassName
   * @return
   */
  def lookupAnalysis(binaryDependency: File, binaryClassName: String): Option[CompileAnalysis]

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