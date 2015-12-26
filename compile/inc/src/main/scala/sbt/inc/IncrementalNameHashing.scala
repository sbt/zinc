package sbt
package inc

import xsbti.api.Source
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
      val classes = relations.declaredClassNames(src)
      val dependentClasses = classes.flatMap(relations.inheritance.internal.reverse)
      dependentClasses.flatMap(relations.declaredClasses.reverse)
    } filter { _.getName == "package.scala" }
  }

  override protected def sameAPI[T](src: T, a: Source, b: Source): Option[APIChange[T]] = {
    if (SameAPI(a, b))
      None
    else {
      val aNameHashes = a._internalOnly_nameHashes
      val bNameHashes = b._internalOnly_nameHashes
      val modifiedNames = ModifiedNames.compareTwoNameHashes(aNameHashes, bNameHashes)
      val apiChange = NamesChange(src, modifiedNames)
      Some(apiChange)
    }
  }

  private def toSrcFile(className: String, relations: Relations): File = {
    val srcs = relations.declaredClasses.reverse(className)
    if (srcs.size == 1)
      srcs.head
    else if (srcs.size == 0)
      sys.error(s"No entry for class $className in declaredClasses relation.")
    else
      sys.error(s"Class $className is marked as declared in more than one file: $srcs")
  }

  private def convertToSrcDependency(relations: Relations, classDependency: Relation[String, String]): Relation[File, File] = {
    def convertRelationMap(m: Map[String, Set[String]]): Map[File, Set[File]] =
      m.map { case (key, values) => toSrcFile(key, relations) -> values.map(toSrcFile(_, relations)) }
    val forwardMap = convertRelationMap(classDependency.forwardMap)
    val reverseMap = convertRelationMap(classDependency.reverseMap)
    Relation.make(forwardMap, reverseMap)
  }

  /** Invalidates sources based on initially detected 'changes' to the sources, products, and dependencies.*/
  override protected def invalidateByExternal(relations: Relations, externalAPIChange: APIChange[String]): Set[File] = {
    val modified = externalAPIChange.modified
    val invalidationReason = memberRefInvalidator.invalidationReason(externalAPIChange)
    log.debug(s"$invalidationReason\nAll member reference dependencies will be considered within this context.")
    // Propagate inheritance dependencies transitively.
    // This differs from normal because we need the initial crossing from externals to sources in this project.
    val externalInheritanceR = relations.inheritance.external
    val byExternalInheritance = externalInheritanceR.reverse(modified)
    log.debug(s"Files invalidated by inheriting from (external) $modified: $byExternalInheritance; now invalidating by inheritance (internally).")
    val transitiveInheritance = byExternalInheritance flatMap { className =>
      invalidateByInheritance(relations, toSrcFile(className, relations))
    }
    val memberRefInvalidationInternal = memberRefInvalidator.get(
      convertToSrcDependency(relations, relations.memberRef.internal),
      relations.names, externalAPIChange)
    val memberRefInvalidationExternal = memberRefInvalidator.get(
      convertToSrcDependency(relations, relations.memberRef.external),
      relations.names, externalAPIChange)

    // Get the member reference dependencies of all sources transitively invalidated by inheritance
    log.debug("Getting direct dependencies of all sources transitively invalidated by inheritance.")
    val memberRefA = transitiveInheritance flatMap memberRefInvalidationInternal
    // Get the sources that depend on externals by member reference.
    // This includes non-inheritance dependencies and is not transitive.
    log.debug(s"Getting sources that directly depend on (external) $modified.")
    val memberRefB = memberRefInvalidationExternal(toSrcFile(modified, relations))
    transitiveInheritance ++ memberRefA ++ memberRefB
  }

  private def invalidateByInheritance(relations: Relations, modified: File): Set[File] = {
    val inheritanceDeps = convertToSrcDependency(relations, relations.inheritance.internal).reverse _
    log.debug(s"Invalidating (transitively) by inheritance from $modified...")
    val transitiveInheritance = transitiveDeps(Set(modified))(inheritanceDeps)
    log.debug("Invalidated by transitive inheritance dependency: " + transitiveInheritance)
    transitiveInheritance
  }

  override protected def invalidateSource(relations: Relations, change: APIChange[File]): Set[File] = {
    log.debug(s"Invalidating ${change.modified}...")
    val transitiveInheritance = invalidateByInheritance(relations, change.modified)
    val reasonForInvalidation = memberRefInvalidator.invalidationReason(change)
    log.debug(s"$reasonForInvalidation\nAll member reference dependencies will be considered within this context.")
    val memberRefInvalidation = memberRefInvalidator.get(convertToSrcDependency(relations, relations.memberRef.internal),
      relations.names, change)
    val memberRef = transitiveInheritance flatMap memberRefInvalidation
    val all = transitiveInheritance ++ memberRef
    all
  }

  override protected def allDeps(relations: Relations): File => Set[File] =
    f => relations.declaredClassNames(f).flatMap(relations.memberRef.internal.reverse).flatMap(relations.declaredClasses.reverse)

}
