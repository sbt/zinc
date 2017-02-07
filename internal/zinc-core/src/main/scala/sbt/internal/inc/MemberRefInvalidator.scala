/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import sbt.internal.util.Relation
import java.io.File
import sbt.util.Logger
import xsbt.api.APIUtil

/**
 * Implements various strategies for invalidating dependencies introduced by member reference.
 *
 * The strategy is represented as a function String => Set[String] where the lambda's parameter is
 * a name of a class that other classes depend on. When you apply that function to a given
 * `className` you get a set of classes that depend on `className` by member reference and should
 * be invalidated due to the APIChange that was passed to a method constructing that function.
 * The center of design of this class is a question:
 *
 *    Why would we apply the function to any other `className` that the one that got modified
 *    and the modification is described by the APIChange?
 *
 * Let's address this question with the following example of code structure:
 *
 * class A
 *
 * class B extends A
 *
 * class C { def foo(a: A) = ??? }
 *
 * class D { def bar(b: B) = ??? }
 *
 * Member reference dependencies on A are B, C. When the api of A changes we consider B and C for
 * invalidation. However, B is also a dependency by inheritance on A so we always invalidate it.
 * The api change to A is relevant when B is considered (inheritance propagates changes to
 * members down the inheritance chain) so we would invalidate B by inheritance and then we would
 * like to invalidate member reference dependencies of B as well. In other words, we have a
 * function because we want to apply it (with the same api change in mind) to all classes
 * invalidated by inheritance of the originally modified class.
 *
 * The specific invalidation strategy is determined based on APIChange that describes a change to
 * the api of a single class.
 *
 * For example, if we get APIChangeDueToMacroDefinition then we invalidate all member reference
 * dependencies unconditionally. On the other hand, if api change is due to modified name hashes
 * of regular members then we'll invalidate sources that use those names.
 */
private[inc] class MemberRefInvalidator(log: Logger, logRecompileOnMacro: Boolean) {
  def get(memberRef: Relation[String, String], usedNames: Relation[String, String], apiChange: APIChange,
    isScalaClass: String => Boolean): String => Set[String] = apiChange match {
    case _: APIChangeDueToMacroDefinition =>
      new InvalidateUnconditionally(memberRef)
    case NamesChange(_, modifiedNames) if modifiedNames.implicitNames.nonEmpty =>
      new InvalidateUnconditionally(memberRef)
    case NamesChange(modifiedClass, modifiedNames) =>
      new NameHashFilteredInvalidator(usedNames, memberRef, modifiedNames.regularNames, isScalaClass)
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

  private class InvalidateDueToMacroDefinition(memberRef: Relation[String, String]) extends (String => Set[String]) {
    def apply(from: String): Set[String] = {
      val invalidated = memberRef.reverse(from)
      if (invalidated.nonEmpty && logRecompileOnMacro) {
        log.info(s"Because $from contains a macro definition, the following dependencies are invalidated unconditionally:\n" +
          formatInvalidated(invalidated))
      }
      invalidated
    }
  }

  private class InvalidateUnconditionally(memberRef: Relation[String, String]) extends (String => Set[String]) {
    def apply(from: String): Set[String] = {
      val invalidated = memberRef.reverse(from)
      if (invalidated.nonEmpty)
        log.debug(s"The following member ref dependencies of $from are invalidated:\n" +
          formatInvalidated(invalidated))
      invalidated
    }
  }

  private def formatInvalidated(invalidated: Set[String]): String = {
    //val sortedFiles = invalidated.toSeq.sortBy(_.getAbsolutePath)
    invalidated.toSeq.sorted.map(cls => "\t" + cls).mkString("\n")
  }

  private class NameHashFilteredInvalidator(
    usedNames: Relation[String, String],
    memberRef: Relation[String, String],
    modifiedNames: Set[String],
    isScalaClass: String => Boolean
  ) extends (String => Set[String]) {

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
  }
}
