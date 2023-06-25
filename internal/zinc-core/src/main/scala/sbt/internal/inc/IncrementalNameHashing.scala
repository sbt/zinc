/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc

import sbt.util.Logger
import xsbti.compile.IncOptions
import xsbti.api.{ AnalyzedClass, DefinitionType }
import xsbt.api.SameAPI

/**
 * Implement the name hashing heuristics to invalidate classes.
 *
 * It's defined `private[inc]` to allow other libraries to extend this class to their discretion.
 * Note that the rest of the Zinc public API will default on the use of the private and final
 * `IncrementalNameHashing`.
 *
 * See [[MemberRefInvalidator]] for more information on how the name heuristics work to invalidate
 * member references.
 */
private[inc] class IncrementalNameHashingCommon(
    log: Logger,
    options: IncOptions,
    profiler: RunProfiler
) extends IncrementalCommon(log, options, profiler) {
  import IncrementalCommon.transitiveDeps

  private val memberRefInvalidator = new MemberRefInvalidator(log, options.logRecompileOnMacro())

  /** @inheritdoc */
  protected def invalidatedPackageObjects(
      invalidatedClasses: Set[String],
      relations: Relations,
      apis: APIs
  ): Set[String] = {
    val findSubclasses = relations.inheritance.internal.reverse _
    val invalidatedClassesAndCodefinedClasses = for {
      cls <- invalidatedClasses.iterator
      file <- relations.definesClass(cls).iterator
      cls1 <- relations.classNames(file)
    } yield cls1

    debug("Invalidate package objects by inheritance only...")
    val invalidatedPackageObjects =
      transitiveDeps(invalidatedClassesAndCodefinedClasses.toSet, log)(findSubclasses)
        .filter(_.endsWith(".package"))
    debug(s"Package object invalidations: ${invalidatedPackageObjects.mkString(", ")}")
    invalidatedPackageObjects
  }

  /** @inheritdoc */
  override protected def findAPIChange(
      className: String,
      a: AnalyzedClass,
      b: AnalyzedClass
  ): Option[APIChange] = {
    if (SameAPI(a, b)) {
      if (SameAPI.hasSameExtraHash(a, b)) None
      else {
        val isATrait = a.api().classApi().definitionType() == DefinitionType.Trait
        val isBTrait = b.api().classApi().definitionType() == DefinitionType.Trait
        if (isATrait && isBTrait) {
          Some(TraitPrivateMembersModified(className))
        } else {
          // if the extra hash does not match up, but the API is "same" we can ignore it.
          // see also https://github.com/sbt/sbt/issues/4441
          debug(s"""different extra api hashes for non-traits:
               |  `${a.name}`: ${a.extraHash()}
               |  `${b.name}`: ${b.extraHash()}
             """.stripMargin)
          None
        }
      }
    } else {
      val aNameHashes = a.nameHashes
      val bNameHashes = b.nameHashes
      val modifiedNames = ModifiedNames.compareTwoNameHashes(aNameHashes, bNameHashes)
      val apiChange = NamesChange(className, modifiedNames)
      Some(apiChange)
    }
  }

  /** @inheritdoc */
  override protected def invalidateClassesExternally(
      relations: Relations,
      externalAPIChange: APIChange,
      isScalaClass: String => Boolean
  ): Set[String] = {
    val modifiedBinaryClassName = externalAPIChange.modifiedClass
    log.debug(memberRefInvalidator.invalidationReason(externalAPIChange))
    log.debug("All member reference dependencies will be considered within this context.")
    val memberRefInv = memberRefInvalidator.get(_, relations.names, externalAPIChange, isScalaClass)

    // Propagate inheritance dependencies transitively.
    // This differs from normal because we need the initial crossing from externals to classes in this project.
    val byExternalInheritance = relations.inheritance.external.reverse(modifiedBinaryClassName)
    log.debug(
      s"Files invalidated by inheriting from (external) $modifiedBinaryClassName: $byExternalInheritance"
    )
    log.debug("Now invalidating by inheritance (internally).")
    val transitiveInheritance = byExternalInheritance.flatMap(invalidateByInheritance(relations, _))

    val localInheritance = relations.localInheritance.external.reverse(modifiedBinaryClassName)

    // Get the member reference dependencies of all classes transitively invalidated by inheritance
    log.debug("Getting direct dependencies of all classes transitively invalidated by inheritance.")
    val memberRefA = transitiveInheritance.flatMap(memberRefInv(relations.memberRef.internal))

    // Get the classes that depend on externals by member reference.
    // This includes non-inheritance dependencies and is not transitive.
    log.debug(s"Getting classes that directly depend on (external) $modifiedBinaryClassName.")
    val memberRefB = memberRefInv(relations.memberRef.external)(modifiedBinaryClassName)

    transitiveInheritance ++ localInheritance ++ memberRefA ++ memberRefB
  }

  private def invalidateByInheritance(relations: Relations, modified: String): Set[String] = {
    val inheritanceDeps = relations.inheritance.internal.reverse _
    log.debug(s"Invalidating (transitively) by inheritance from $modified...")
    val transitiveInheritance = transitiveDeps(Set(modified), log)(inheritanceDeps)
    log.debug("Invalidated by transitive inheritance dependency: " + transitiveInheritance)
    transitiveInheritance
  }

  private def invalidateByLocalInheritance(relations: Relations, modified: String): Set[String] = {
    val localInheritanceDeps = relations.localInheritance.internal.reverse(modified)
    if (localInheritanceDeps.nonEmpty)
      log.debug(s"Invalidate by local inheritance: $modified -> $localInheritanceDeps")
    localInheritanceDeps
  }

  /** @inheritdoc */
  override protected def invalidateClassesInternally(
      relations: Relations,
      change: APIChange,
      isScalaClass: String => Boolean
  ): Set[String] = {
    val modifiedClass = change.modifiedClass
    val memberRefInv = memberRefInvalidator.get(_, relations.names, change, isScalaClass)

    val transitiveInheritance = invalidateByInheritance(relations, modifiedClass)
    val reason1 = s"The invalidated class names inherit directly or transitively on $modifiedClass."
    profiler.registerEvent(InheritanceKind, List(modifiedClass), transitiveInheritance, reason1)

    val localInheritance = transitiveInheritance.flatMap(invalidateByLocalInheritance(relations, _))
    val reason2 =
      s"The invalidated class names inherit (via local inheritance) directly or transitively on $modifiedClass."
    profiler.registerEvent(LocalInheritanceKind, transitiveInheritance, localInheritance, reason2)

    val memberRef = transitiveInheritance.flatMap(memberRefInv(relations.memberRef.internal))
    val reason3 = s"The invalidated class names refer directly or transitively to $modifiedClass."
    profiler.registerEvent(MemberReferenceKind, transitiveInheritance, memberRef, reason3)

    val all = transitiveInheritance ++ localInheritance ++ memberRef
    log.debug {
      if (all.isEmpty) s"Change $change does not affect any class."
      else {
        val reason = memberRefInvalidator.invalidationReason(change)
        def ppxs(s: String, xs: Set[String]) = if (xs.isEmpty) "" else s"$s: $xs"
        s"""Change $change invalidates ${all.size} classes due to $reason
           |  > ${ppxs("by transitive inheritance", transitiveInheritance)}
           |  > ${ppxs("by local inheritance", localInheritance)}
           |  > ${ppxs("by member reference", memberRef)}
        """.stripMargin
      }
    }
    all
  }

  /** @inheritdoc */
  override protected def findClassDependencies(
      className: String,
      relations: Relations
  ): Set[String] = relations.memberRef.internal.reverse(className)
}

private final class IncrementalNameHashing(log: Logger, options: IncOptions, profiler: RunProfiler)
    extends IncrementalNameHashingCommon(log, options, profiler)
