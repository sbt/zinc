/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbti.compile.IncOptions
import java.io.File
import xsbti.api.AnalyzedClass

private final class IncrementalAntStyle(log: sbt.util.Logger, options: IncOptions) extends IncrementalCommon(log, options) {

  /** Ant-style mode doesn't do anything special with package objects */
  override protected def invalidatedPackageObjects(invalidatedClasses: Set[String], relations: Relations,
    apis: APIs): Set[String] = Set.empty

  /** In Ant-style mode we don't need to compare APIs because we don't perform any invalidation */
  override protected def sameAPI(className: String, a: AnalyzedClass, b: AnalyzedClass): Option[APIChange] = None

  /** In Ant-style mode we don't perform any invalidation */
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange,
    isScalaClass: String => Boolean): Set[String] = Set.empty

  /** In Ant-style mode we don't perform any invalidation */
  override protected def invalidateClass(relations: Relations, change: APIChange,
    isScalaClass: String => Boolean): Set[String] = Set.empty

  /** In Ant-style mode we don't need to perform any dependency analysis hence we can always return an empty set. */
  override protected def allDeps(relations: Relations): (String) => Set[String] = _ => Set.empty

}
