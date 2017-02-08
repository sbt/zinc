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
import xsbti.api.AnalyzedClass
import xsbt.api.{ APIUtil, SameAPI }

/**
 * Implementation of incremental algorithm known as "name hashing". It differs from the default implementation
 * by applying pruning (filter) of member reference dependencies based on used and modified simple names.
 *
 * See MemberReferenceInvalidationStrategy for some more information.
 */
private final class IncrementalNameHashing(log: sbt.util.Logger, options: IncOptions) extends IncrementalCommon(log, options) {

  private val memberRefInvalidator = new MemberRefInvalidator(log, options.logRecompileOnMacro())

  // Package objects are fragile: if they inherit from an invalidated class, get "class file needed by package is missing" error
  //  This might be too conservative: we probably only need package objects for packages of invalidated classes.
  protected def invalidatedPackageObjects(invalidatedClasses: Set[String], relations: Relations,
    apis: APIs): Set[String] = {
    transitiveDeps(invalidatedClasses, logging = false)(relations.inheritance.internal.reverse) filter {
      _.endsWith(".package")
    }
  }

  override protected def sameAPI(className: String, a: AnalyzedClass, b: AnalyzedClass): Option[APIChange] = {
    if (SameAPI(a, b))
      None
    else {
      val aNameHashes = a.nameHashes
      val bNameHashes = b.nameHashes
      val modifiedNames = ModifiedNames.compareTwoNameHashes(aNameHashes, bNameHashes)
      val apiChange = NamesChange(className, modifiedNames)
      Some(apiChange)
    }
  }

  /** Invalidates classes based on initially detected 'changes' to the sources, products, and dependencies.*/
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange,
    isScalaClass: String => Boolean): Set[String] = {
    val modifiedBinaryClassName = externalAPIChange.modifiedClass
    val invalidationReason = memberRefInvalidator.invalidationReason(externalAPIChange)
    log.debug(s"$invalidationReason\nAll member reference dependencies will be considered within this context.")
    // Propagate inheritance dependencies transitively.
    // This differs from normal because we need the initial crossing from externals to classes in this project.
    val externalInheritanceR = relations.inheritance.external
    val byExternalInheritance = externalInheritanceR.reverse(modifiedBinaryClassName)
    log.debug(s"Files invalidated by inheriting from (external) $modifiedBinaryClassName: $byExternalInheritance; now invalidating by inheritance (internally).")
    val transitiveInheritance = byExternalInheritance flatMap { className =>
      invalidateByInheritance(relations, className)
    }
    val localInheritance = relations.localInheritance.external.reverse(modifiedBinaryClassName)
    val memberRefInvalidationInternal = memberRefInvalidator.get(
      relations.memberRef.internal,
      relations.names, externalAPIChange, isScalaClass
    )
    val memberRefInvalidationExternal = memberRefInvalidator.get(
      relations.memberRef.external,
      relations.names, externalAPIChange, isScalaClass
    )

    // Get the member reference dependencies of all classes transitively invalidated by inheritance
    log.debug("Getting direct dependencies of all classes transitively invalidated by inheritance.")
    val memberRefA = transitiveInheritance flatMap memberRefInvalidationInternal
    // Get the classes that depend on externals by member reference.
    // This includes non-inheritance dependencies and is not transitive.
    log.debug(s"Getting classes that directly depend on (external) $modifiedBinaryClassName.")
    val memberRefB = memberRefInvalidationExternal(modifiedBinaryClassName)
    transitiveInheritance ++ localInheritance ++ memberRefA ++ memberRefB
  }

  private def invalidateByInheritance(relations: Relations, modified: String): Set[String] = {
    val inheritanceDeps = relations.inheritance.internal.reverse _
    log.debug(s"Invalidating (transitively) by inheritance from $modified...")
    val transitiveInheritance = transitiveDeps(Set(modified))(inheritanceDeps)
    log.debug("Invalidated by transitive inheritance dependency: " + transitiveInheritance)
    transitiveInheritance
  }

  private def invalidateByLocalInheritance(relations: Relations, modified: String): Set[String] = {
    val localInheritanceDeps = relations.localInheritance.internal.reverse(modified)
    if (localInheritanceDeps.nonEmpty)
      log.debug(s"Invalidate by local inheritance: $modified -> $localInheritanceDeps")
    localInheritanceDeps
  }

  override protected def invalidateClass(relations: Relations, change: APIChange, isScalaClass: String => Boolean): Set[String] = {
    log.debug(s"Invalidating ${change.modifiedClass}...")
    val modifiedClass = change.modifiedClass
    val transitiveInheritance = invalidateByInheritance(relations, modifiedClass)
    val localInheritance = transitiveInheritance.flatMap(invalidateByLocalInheritance(relations, _))
    val reasonForInvalidation = memberRefInvalidator.invalidationReason(change)
    log.debug(s"$reasonForInvalidation\nAll member reference dependencies will be considered within this context.")
    val memberRefSrcDeps = relations.memberRef.internal
    val memberRefInvalidation = memberRefInvalidator.get(memberRefSrcDeps, relations.names, change, isScalaClass)
    val memberRef = transitiveInheritance flatMap memberRefInvalidation
    val all = transitiveInheritance ++ localInheritance ++ memberRef
    all
  }

  override protected def allDeps(relations: Relations): (String) => Set[String] =
    cls => relations.memberRef.internal.reverse(cls)

}
