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
import xsbti.UseScope
import xsbti.api.AnalyzedClass
import xsbti.compile.IncOptions

/**
 * Names that became visible in (or vanished from) package scopes during a compiler cycle:
 * `addedNames` are (package, simple name) pairs newly defined at package level,
 * `packagesWithImplicitChanges` gained or changed an implicit package-scoped member, and
 * `removedPackages` no longer contain any top-level class. See sbt/zinc#226.
 */
private[inc] final case class PackageScopeChanges(
    addedNames: Set[(String, String)],
    packagesWithImplicitChanges: Set[String],
    removedPackages: Set[String]
) {
  def isEmpty: Boolean =
    addedNames.isEmpty && packagesWithImplicitChanges.isEmpty && removedPackages.isEmpty
}

/**
 * Invalidates sources that used a simple name a package scope change may now resolve
 * differently, via a wildcard import or the enclosing package. Nothing references a brand-new
 * definition, so regular name-hashing invalidation cannot reach these sources. See sbt/zinc#226.
 *
 * Bounded: a name nobody uses invalidates nothing. Detection reads name hashes and product
 * relations, which are always recorded, rather than API structures, which are minimized without
 * `apiDebug` (see [[xsbt.api.APIUtil.minimize]]) and absent without `storeApis`.
 */
private[inc] final class PackageScopeInvalidator(options: IncOptions, log: Logger) {

  def invalidate(
      previous: Analysis,
      analysis: Analysis,
      recompiledClasses: Set[String]
  ): Set[String] = {
    val changes = detectChanges(previous, analysis, recompiledClasses)
    if (changes.isEmpty) Set.empty
    else {
      log.debug(s"Detected package scope changes: $changes")
      invalidateChanges(changes, analysis.relations, recompiledClasses)
    }
  }

  private def packageOf(className: String): String = {
    val i = className.lastIndexOf('.')
    if (i < 0) "" else className.substring(0, i)
  }

  private def simpleNameOf(className: String): String =
    className.substring(className.lastIndexOf('.') + 1)

  // A package object or the synthetic object wrapping Scala 3 top-level definitions:
  // its members belong to the enclosing package scope.
  private def isPackageScopedContainer(className: String): Boolean = {
    val simple = simpleNameOf(className)
    simple == "package" || simple.endsWith("$package")
  }

  private def hashesIn(ac: AnalyzedClass, scope: UseScope): Map[String, Int] =
    ac.nameHashes.iterator.filter(_.scope == scope).map(nh => nh.name -> nh.hash).toMap

  /**
   * Detects names that appeared in (or vanished from) a package scope this cycle: new top-level
   * classes/objects, companions added beside an existing class or object, new members of a
   * package object or Scala 3 `$package` object, and packages that lost their last top-level
   * class. Additions reusing an existing simple name (e.g. a non-implicit overload) are left to
   * regular name-hash invalidation; the per-shape reasoning is inline below.
   */
  private def detectChanges(
      previous: Analysis,
      analysis: Analysis,
      recompiledClasses: Set[String]
  ): PackageScopeChanges = {
    val previousClasses = previous.apis.allInternalClasses
    val currentClasses = analysis.apis.allInternalClasses

    // The prefix of a nested class is another class, the prefix of a top-level class a package.
    def isTopLevel(className: String): Boolean = {
      val prefix = packageOf(className)
      prefix.isEmpty || !(currentClasses.contains(prefix) || previousClasses.contains(prefix))
    }
    def addedIn(oldAc: AnalyzedClass, newAc: AnalyzedClass, scope: UseScope): Set[String] =
      hashesIn(newAc, scope).keySet -- hashesIn(oldAc, scope).keySet
    def changedIn(oldAc: AnalyzedClass, newAc: AnalyzedClass, scope: UseScope): Set[String] = {
      val old = hashesIn(oldAc, scope)
      hashesIn(newAc, scope).collect {
        case (name, hash) if !old.get(name).contains(hash) => name
      }.toSet
    }

    val addedTopLevel = (recompiledClasses -- previousClasses).filter(isTopLevel)
    val addedClasses = addedTopLevel.filterNot(isPackageScopedContainer)

    val addedCompanions: Set[(String, Boolean)] = // (class name, added side is an implicit object)
      recompiledClasses.iterator
        .filter(c => previousClasses.contains(c) && isTopLevel(c))
        .filterNot(isPackageScopedContainer)
        .flatMap { c =>
          // Companion class and object share the name `c` but produce distinct binary products
          // `c` and `c$`. A new object side shows up in the product relation; a new class side
          // hides behind the object's mirror class, so it needs the stored API (fallback below).
          def hasObjectSide(rel: Relations) = rel.productClassName.forward(c).contains(c + "$")
          val objectSideBefore = hasObjectSide(previous.relations)
          val objectAdded = hasObjectSide(analysis.relations) && !objectSideBefore
          val oldAc = previous.apis.internalAPI(c)
          val newAc = analysis.apis.internalAPI(c)
          val classAdded =
            if (options.storeApis) {
              def hasClassSide(ac: AnalyzedClass) = ac.api.classApi.structure.parents.nonEmpty
              oldAc.apiHash != newAc.apiHash && !hasClassSide(oldAc) && hasClassSide(newAc)
            } else {
              // No stored API to see the added class side. Conservatively treat any API change
              // of a class that already had an object side as a possible class-side addition;
              // class-only edits are unaffected, and this never under-compiles.
              objectSideBefore && oldAc.apiHash != newAc.apiHash
            }
          if (!objectAdded && !classAdded) Nil
          else {
            val isImplicitObject = objectAdded && newAc.api.objectApi.modifiers.isImplicit
            (c -> isImplicitObject) :: Nil
          }
        }
        .toSet

    // Members newly visible in a package scope via recompiled package objects / `$package`
    // objects (a brand-new container has an empty previous API, so all its members count as
    // added). Container name hashes cover only direct members - a nested class or object is its
    // own internal class - so a nested implicit stays with that object and regular name-hashing
    // handles it.
    val containerChanges: Seq[(String, Set[String], Boolean)] =
      recompiledClasses.filter(isPackageScopedContainer).toSeq.flatMap { container =>
        val oldAc = previous.apis.internalAPI(container)
        val newAc = analysis.apis.internalAPI(container)
        val addedDefaults = addedIn(oldAc, newAc, UseScope.Default)
        val changedImplicits = changedIn(oldAc, newAc, UseScope.Implicit)
        if (addedDefaults.isEmpty && changedImplicits.isEmpty) None
        else Some((packageOf(container), addedDefaults, changedImplicits.nonEmpty))
      }

    // Packages that no longer contain any top-level class.
    val removedPackages: Set[String] = {
      val removedClasses = previousClasses.filterNot(currentClasses).filter(isTopLevel)
      if (removedClasses.isEmpty) Set.empty
      else {
        def selfAndAncestors(pkg: String): List[String] =
          if (pkg.isEmpty) Nil else pkg :: selfAndAncestors(packageOf(pkg))
        def populated(classes: collection.Set[String]): Set[String] =
          classes.iterator.filter(isTopLevel).flatMap(c => selfAndAncestors(packageOf(c))).toSet
        populated(removedClasses) -- populated(currentClasses)
      }
    }

    PackageScopeChanges(
      addedNames = addedClasses.map(c => packageOf(c) -> simpleNameOf(c)) ++
        addedCompanions.map { case (c, _) => packageOf(c) -> simpleNameOf(c) } ++
        containerChanges.flatMap { case (pkg, defaults, _) => defaults.map(pkg -> _) },
      packagesWithImplicitChanges =
        containerChanges.collect { case (pkg, _, true) => pkg }.toSet ++
          addedCompanions.collect { case (c, true) => packageOf(c) },
      removedPackages = removedPackages
    )
  }

  /**
   * A named addition invalidates files that used that name; an implicit change invalidates every
   * file that can see the package (implicit resolution needs no name). A file sees package `p` if
   * one of its classes lives in `p` or below, or it used every segment of `p`'s path - what an
   * import of `p`, wildcard or not, records.
   */
  private def invalidateChanges(
      changes: PackageScopeChanges,
      relations: Relations,
      recompiledClasses: Set[String]
  ): Set[String] = {
    val usedNames = relations.names.toMultiMap
    val segments: Map[String, Array[String]] =
      (changes.addedNames.map(_._1) ++ changes.packagesWithImplicitChanges ++
        changes.removedPackages).iterator.map(p => p -> p.split('.')).toMap
    // Used names of top-level import statements are attributed to the first class of a file,
    // so both name usage and package visibility are judged per source file.
    val invalidated = relations.classes.forwardMap.collect {
      case (_, classesInFile) if !classesInFile.exists(recompiledClasses) => classesInFile
    }.filter { classesInFile =>
      val fileNames = classesInFile.flatMap(usedNames.getOrElse(_, Set.empty)).map(_.name)
      val filePackages = classesInFile.map(packageOf)
      def sees(pkg: String): Boolean =
        filePackages.exists(fp => fp == pkg || (pkg.nonEmpty && fp.startsWith(pkg + "."))) ||
          (pkg.nonEmpty && segments(pkg).forall(fileNames.contains))
      changes.addedNames.exists { case (pkg, name) => fileNames.contains(name) && sees(pkg) } ||
      changes.packagesWithImplicitChanges.exists(sees) ||
      changes.removedPackages.exists(pkg => segments(pkg).forall(fileNames.contains))
    }
    invalidated.flatten.toSet
  }
}
