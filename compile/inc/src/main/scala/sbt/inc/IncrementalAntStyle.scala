package sbt
package inc

import java.io.File
import xsbti.api.AnalyzedClass

private final class IncrementalAntStyle(log: Logger, options: IncOptions) extends IncrementalCommon(log, options) {

  /** Ant-style mode doesn't do anything special with package objects */
  override protected def invalidatedPackageObjects(invalidatedClasses: Set[String], relations: Relations): Set[String] = Set.empty

  /** In Ant-style mode we don't need to compare APIs because we don't perform any invalidation */
  override protected def sameAPI(className: String, a: AnalyzedClass, b: AnalyzedClass): Option[APIChange] = None

  /** In Ant-style mode we don't perform any invalidation */
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange, classToSrcMapper: ClassToSourceMapper): Set[String] = Set.empty

  /** In Ant-style mode we don't perform any invalidation */
  override protected def invalidateClass(relations: Relations, change: APIChange, classToSourceMapper: ClassToSourceMapper): Set[String] = Set.empty

  /** In Ant-style mode we don't need to perform any dependency analysis hence we can always return an empty set. */
  override protected def allDeps(relations: Relations): (String) => Set[String] = _ => Set.empty

}
