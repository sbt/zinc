package sbt.inc

import sbt.Relation
import java.io.File
import sbt.Logger
import xsbt.api.APIUtil

/**
 * Implements various strategies for invalidating dependencies introduced by member reference.
 *
 * The strategy is represented as function T => Set[File] where T is a source file that other
 * source files depend on. When you apply that function to given element `src` you get set of
 * files that depend on `src` by member reference and should be invalidated due to api change
 * that was passed to a method constructing that function. There are two questions that arise:
 *
 *    1. Why is signature T => Set[File] and not T => Set[T] or File => Set[File]?
 *    2. Why would we apply that function to any other `src` that then one that got modified
 *       and the modification is described by APIChange?
 *
 * Let's address the second question with the following example of source code structure:
 *
 * // A.scala
 * class A
 *
 * // B.scala
 * class B extends A
 *
 * // C.scala
 * class C { def foo(a: A) = ??? }
 *
 * // D.scala
 * class D { def bar(b: B) = ??? }
 *
 * Member reference dependencies on A.scala are B.scala, C.scala. When the api of A changes
 * then we would consider B and C for invalidation. However, B is also a dependency by inheritance
 * so we always invalidate it. The api change to A is relevant when B is considered (because
 * of how inheritance works) so we would invalidate B by inheritance and then we would like to
 * invalidate member reference dependencies of B as well. In other words, we have a function
 * because we want to apply it (with the same api change in mind) to all src files invalidated
 * by inheritance of the originally modified file.
 *
 * The first question is a bit more straightforward to answer. We always invalidate internal
 * source files (in given project) that are represented as File but they might depend either on
 * internal source files (then T=File) or they can depend on external class name (then T=String).
 *
 * The specific invalidation strategy is determined based on APIChange that describes a change to api
 * of a single source file.
 *
 * For example, if we get APIChangeDueToMacroDefinition then we invalidate all member reference
 * dependencies unconditionally. On the other hand, if api change is due to modified name hashes
 * of regular members then we'll invalidate sources that use those names.
 */
private[inc] class MemberRefInvalidator(log: Logger) {
  def get(memberRef: Relation[String, String], usedNames: Relation[String, String], apiChange: APIChange,
    classToSourceMapper: ClassToSourceMapper): String => Set[String] = apiChange match {
    case _: APIChangeDueToMacroDefinition =>
      new InvalidateUnconditionally(memberRef)
    case NamesChange(_, modifiedNames) if modifiedNames.implicitNames.nonEmpty =>
      new InvalidateUnconditionally(memberRef)
    case NamesChange(modifiedClass, modifiedNames) =>
      new NameHashFilteredInvalidator(usedNames, memberRef, modifiedNames.regularNames, classToSourceMapper)
  }

  def invalidationReason(apiChange: APIChange): String = apiChange match {
    case APIChangeDueToMacroDefinition(modifiedSrcFile) =>
      s"The $modifiedSrcFile source file declares a macro."
    case NamesChange(modifiedClass, modifiedNames) if modifiedNames.implicitNames.nonEmpty =>
      s"""|The $modifiedClass has the following implicit definitions changed:
				|\t${modifiedNames.implicitNames.mkString(", ")}.""".stripMargin
    case NamesChange(modifiedClass, modifiedNames) =>
      s"""|The $modifiedClass has the following regular definitions changed:
				|\t${modifiedNames.regularNames.mkString(", ")}.""".stripMargin
  }

  private class InvalidateUnconditionally(memberRef: Relation[String, String]) extends (String => Set[String]) {
    def apply(from: String): Set[String] = {
      val invalidated = memberRef.reverse(from)
      if (invalidated.nonEmpty)
        log.debug(s"The following member ref dependencies of $from are invalidated:\n" +
          formatInvalidated(invalidated))
      invalidated
    }
    private def formatInvalidated(invalidated: Set[String]): String = {
      //val sortedFiles = invalidated.toSeq.sortBy(_.getAbsolutePath)
      invalidated.toSeq.sorted.map(cls => "\t" + cls).mkString("\n")
    }
  }

  private class NameHashFilteredInvalidator(
      usedNames: Relation[String, String],
      memberRef: Relation[String, String],
      modifiedNames: Set[String],
      classToSourceMapper: ClassToSourceMapper) extends (String => Set[String]) {

    def apply(to: String): Set[String] = {
      val dependent = memberRef.reverse(to)
      filteredDependencies(dependent)
    }
    private def filteredDependencies(dependent: Set[String]): Set[String] = {
      dependent.filter {
        case from if isScalaClass(from) =>
          val usedNamesInDependent = usedNames.forward(from)
          val modifiedAndUsedNames = modifiedNames intersect usedNamesInDependent
          if (modifiedAndUsedNames.isEmpty) {
            log.debug(s"None of the modified names appears in source file of $from. This dependency is not being considered for invalidation.")
            false
          } else {
            log.debug(s"The following modified names cause invalidation of $from: $modifiedAndUsedNames")
            true
          }
        case from =>
          log.debug(s"Name hashing optimization doesn't apply to non-Scala dependency: $from")
          true
      }
    }
    private def isScalaClass(className: String): Boolean = {
      val srcFiles = classToSourceMapper.toSrcFile(className)
      srcFiles.forall(srcFile => APIUtil.isScalaSourceName(srcFile.getName))
    }

    private def classNameToSrcFile(className: String): File = {
      val allSrcs = classToSourceMapper.toSrcFile(className)
      if (allSrcs.isEmpty)
        sys.error(s"Can't find corresponding source file for $className")
      else if (allSrcs.size > 1)
        sys.error(s"Multiple sources found corresponding to the same class $className: $allSrcs")
      else
        allSrcs.head
    }
  }
}
