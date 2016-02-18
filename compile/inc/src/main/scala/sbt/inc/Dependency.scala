package sbt.inc

import xsbti.api.AnalyzedClass
import xsbti.DependencyContext

/**
 * Represents the kind of dependency that exists between `sourceFile` and either `targetFile`
 * or `targetClassName`.
 *
 * `InternalDependency` represent dependencies that exist between the files of a same project,
 * while `ExternalDependency` represent cross-project dependencies.
 */
private[inc] final case class InternalDependency(sourceClassName: String, targetClassName: String, context: DependencyContext)
private[inc] final case class ExternalDependency(sourceClassName: String, targetClassName: String,
  targetClass: AnalyzedClass, context: DependencyContext)
