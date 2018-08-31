/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
    debug("Invalidate package objects by inheritance only...")
    val invalidatedPackageObjects =
      transitiveDeps(invalidatedClasses, log)(findSubclasses).filter(_.endsWith(".package"))
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
          // As we don't cover more cases here, we protect ourselves from a potential programming error
          sys.error(
            s"""A fatal error happened in `SameAPI`: different extra api hashes for no traits!
               |  `${a.name}`: ${a.extraHash()}
               |  `${b.name}`: ${b.extraHash()}
             """.stripMargin
          )
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
    val invalidationReason = memberRefInvalidator.invalidationReason(externalAPIChange)
    log.debug(
      s"$invalidationReason\nAll member reference dependencies will be considered within this context.")
    // Propagate inheritance dependencies transitively.
    // This differs from normal because we need the initial crossing from externals to classes in this project.
    val externalInheritanceR = relations.inheritance.external
    val byExternalInheritance = externalInheritanceR.reverse(modifiedBinaryClassName)
    log.debug(
      s"Files invalidated by inheriting from (external) $modifiedBinaryClassName: $byExternalInheritance; now invalidating by inheritance (internally).")
    val transitiveInheritance = byExternalInheritance flatMap { className =>
      invalidateByInheritance(relations, className)
    }
    val localInheritance = relations.localInheritance.external.reverse(modifiedBinaryClassName)
    val memberRefInvalidationInternal = memberRefInvalidator.get(
      relations.memberRef.internal,
      relations.names,
      externalAPIChange,
      isScalaClass
    )
    val memberRefInvalidationExternal = memberRefInvalidator.get(
      relations.memberRef.external,
      relations.names,
      externalAPIChange,
      isScalaClass
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
    val transitiveInheritance = transitiveDeps(Set(modified), log)(inheritanceDeps)
    log.debug("Invalidated by transitive inheritance dependency: " + transitiveInheritance)
    transitiveInheritance
  }

  /** @inheritdoc */
  override protected def invalidateClassesInternally(
      relations: Relations,
      change: APIChange,
      isScalaClass: String => Boolean
  ): Set[String] = {
    def invalidateByLocalInheritance(relations: Relations, modified: String): Set[String] = {
      val localInheritanceDeps = relations.localInheritance.internal.reverse(modified)
      if (localInheritanceDeps.nonEmpty)
        log.debug(s"Invalidate by local inheritance: $modified -> $localInheritanceDeps")
      localInheritanceDeps
    }

    val modifiedClass = change.modifiedClass
    val transitiveInheritance = invalidateByInheritance(relations, modifiedClass)
    profiler.registerEvent(
      zprof.InvalidationEvent.InheritanceKind,
      List(modifiedClass),
      transitiveInheritance,
      s"The invalidated class names inherit directly or transitively on ${modifiedClass}."
    )

    val localInheritance =
      transitiveInheritance.flatMap(invalidateByLocalInheritance(relations, _))
    profiler.registerEvent(
      zprof.InvalidationEvent.LocalInheritanceKind,
      transitiveInheritance,
      localInheritance,
      s"The invalidated class names inherit (via local inheritance) directly or transitively on ${modifiedClass}."
    )

    val memberRefSrcDeps = relations.memberRef.internal
    val memberRefInvalidation =
      memberRefInvalidator.get(memberRefSrcDeps, relations.names, change, isScalaClass)
    val memberRef = transitiveInheritance flatMap memberRefInvalidation
    profiler.registerEvent(
      zprof.InvalidationEvent.MemberReferenceKind,
      transitiveInheritance,
      memberRef,
      s"The invalidated class names refer directly or transitively to ${modifiedClass}."
    )
    val all = transitiveInheritance ++ localInheritance ++ memberRef

    def debugMessage: String = {
      if (all.isEmpty) s"Change $change does not affect any class."
      else {
        val byTransitiveInheritance =
          if (transitiveInheritance.nonEmpty) s"by transitive inheritance: $transitiveInheritance"
          else ""
        val byLocalInheritance =
          if (localInheritance.nonEmpty) s"by local inheritance: $localInheritance" else ""
        val byMemberRef =
          if (memberRef.nonEmpty) s"by member reference: $memberRef" else ""

        s"""Change $change invalidates ${all.size} classes due to ${memberRefInvalidator
             .invalidationReason(change)}
           |\t> $byTransitiveInheritance
           |\t> $byLocalInheritance
           |\t> $byMemberRef
        """.stripMargin
      }
    }

    log.debug(debugMessage)
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
