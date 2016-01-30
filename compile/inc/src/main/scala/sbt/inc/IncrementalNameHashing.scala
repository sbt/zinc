package sbt
package inc

import xsbti.api.{ ToplevelApiHash, Source }
import xsbt.api.SameAPI
import java.io.File

/**
 * Implementation of incremental algorithm known as "name hashing". It differs from the default implementation
 * by applying pruning (filter) of member reference dependencies based on used and modified simple names.
 *
 * See MemberReferenceInvalidationStrategy for some more information.
 */
private final class IncrementalNameHashing(log: Logger, options: IncOptions) extends IncrementalCommon(log, options) {

  private val memberRefInvalidator = new MemberRefInvalidator(log)

  // Package objects are fragile: if they inherit from an invalidated source, get "class file needed by package is missing" error
  //  This might be too conservative: we probably only need package objects for packages of invalidated sources.
  override protected def invalidatedPackageObjects(invalidated: Set[File], relations: Relations): Set[File] = {
    invalidated flatMap { src =>
      val classes = relations.classNames(src)
      val dependentClasses = classes.flatMap(relations.inheritance.internal.reverse)
      dependentClasses.flatMap(relations.classes.reverse)
    } filter { _.getName == "package.scala" }
  }

  override protected def sameAPI[T](src: T, a: Source, b: Source): Option[APIChange[T]] = {
    if (SameAPI(a, b))
      None
    else {
      val aNameHashes = a._internalOnly_nameHashes
      val bNameHashes = b._internalOnly_nameHashes
      val modifiedNames = ModifiedNames.compareTwoNameHashes(aNameHashes, bNameHashes)
      val modifiedToplevelClasses = compareToplevelHashes(a.toplevelApiHashes.toSet, b.toplevelApiHashes.toSet)
      val apiChange = NamesChange(src, modifiedToplevelClasses, modifiedNames)
      Some(apiChange)
    }
  }

  private def compareToplevelHashes(xs: Set[ToplevelApiHash], ys: Set[ToplevelApiHash]): Set[String] = {
    val differentHashes = (xs union ys) diff (xs intersect ys)
    differentHashes.map(_.name)
  }

  /** Invalidates sources based on initially detected 'changes' to the sources, products, and dependencies.*/
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange[String], classToSrcMapper: ClassToSourceMapper): Set[String] = {
    val modified = externalAPIChange.modified
    val invalidationReason = memberRefInvalidator.invalidationReason(externalAPIChange)
    log.debug(s"$invalidationReason\nAll member reference dependencies will be considered within this context.")
    // Propagate inheritance dependencies transitively.
    // This differs from normal because we need the initial crossing from externals to sources in this project.
    val externalInheritanceR = relations.inheritance.external
    val byExternalInheritance = externalInheritanceR.reverse(modified)
    log.debug(s"Files invalidated by inheriting from (external) $modified: $byExternalInheritance; now invalidating by inheritance (internally).")
    val transitiveInheritance = byExternalInheritance flatMap { className =>
      invalidateByInheritance(relations, className)
    }
    val localInheritance = relations.localInheritance.external.reverse(modified)
    val memberRefInvalidationInternal = memberRefInvalidator.get(relations.memberRef.internal,
      relations.names, externalAPIChange, classToSrcMapper)
    val memberRefInvalidationExternal = memberRefInvalidator.get(relations.memberRef.external,
      relations.names, externalAPIChange, classToSrcMapper)

    // Get the member reference dependencies of all sources transitively invalidated by inheritance
    log.debug("Getting direct dependencies of all sources transitively invalidated by inheritance.")
    val memberRefA = transitiveInheritance flatMap memberRefInvalidationInternal
    // Get the sources that depend on externals by member reference.
    // This includes non-inheritance dependencies and is not transitive.
    log.debug(s"Getting sources that directly depend on (external) $modified.")
    val memberRefB = memberRefInvalidationExternal(modified)
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
    log.debug("Invalidated by local inheritance dependency: " + localInheritanceDeps)
    localInheritanceDeps
  }

  override protected def invalidateSource(relations: Relations, change: APIChange[File], classToSourceMapper: ClassToSourceMapper): Set[File] = {
    log.debug(s"Invalidating ${change.modified}...")
    val modifiedClassesInSrc = changedClassNames(change, relations)
    val transitiveInheritance = modifiedClassesInSrc.flatMap(invalidateByInheritance(relations, _))
    val localInheritance = modifiedClassesInSrc.flatMap(invalidateByLocalInheritance(relations, _))
    val reasonForInvalidation = memberRefInvalidator.invalidationReason(change)
    log.debug(s"$reasonForInvalidation\nAll member reference dependencies will be considered within this context.")
    val memberRefSrcDeps = relations.memberRef.internal
    val memberRefInvalidation = memberRefInvalidator.get(memberRefSrcDeps, relations.names, change, classToSourceMapper)
    val memberRef = transitiveInheritance flatMap memberRefInvalidation
    val all = transitiveInheritance ++ localInheritance ++ memberRef
    val allSrcs = all.flatMap(classToSourceMapper.toSrcFile)
    allSrcs
  }

  private def changedClassNames(change: APIChange[File], relations: Relations): Set[String] = change match {
    case _: APIChangeDueToMacroDefinition[_] | _: SourceAPIChange[_] => relations.classNames(change.modified)
    case NamesChange(_, modifiedToplevelClasses, _)                  => modifiedToplevelClasses
  }

  override protected def allDeps(relations: Relations): File => Set[File] =
    f => relations.classNames(f).flatMap(relations.memberRef.internal.reverse).flatMap(relations.classes.reverse)

}
