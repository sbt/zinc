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

package sbt.internal.inc

import scala.jdk.CollectionConverters._
import sbt.util.Logger
import xsbti.UseScope

private[inc] final class InvalidationLog(log: Logger, relationsDebug: Boolean) {
  private val prefixed = new Incremental.PrefixingLogger("[inv] ")(log)
  private[inc] val detailLogger: Logger = if (relationsDebug) prefixed else Logger.Null

  def debug(message: => String): Unit = prefixed.debug(message)

  def detail(message: => String): Unit =
    if (relationsDebug) prefixed.debug(message)
}

private[inc] object InvalidationLog {

  /**
   * Renders a section, omitting empty groups. Returns an empty string when every group is empty
   * and `whenEmpty` is absent, so callers must either guard the log call or provide a fallback.
   */
  def section(
      title: String,
      groups: Iterable[(String, Iterable[String])],
      whenEmpty: Option[String] = None,
  ): String = {
    val renderedGroups = groups.iterator.flatMap { case (label, values) =>
      val sortedValues = values.iterator.toVector.sorted
      if (sortedValues.isEmpty) None
      else {
        val renderedValues = sortedValues.iterator
          .flatMap(_.linesIterator)
          .map(line => s"    $line")
          .mkString("\n")
        Some(s"  $label:\n$renderedValues")
      }
    }.toVector
    val body = renderedGroups ++ (if (renderedGroups.isEmpty) whenEmpty.map(s => s"  $s") else None)
    if (body.isEmpty) "" else (title +: body).mkString("\n")
  }

  def cycleOutcome(
      cycleNum: Int,
      nextInvalidations: Set[String],
      continue: Boolean,
      isFullCompilation: Boolean,
  ): String =
    if (continue)
      section(
        s"Cycle $cycleNum outcome",
        Seq("next-cycle invalidations" -> nextInvalidations)
      )
    else if (nextInvalidations.nonEmpty)
      section(
        s"Cycle $cycleNum outcome",
        Seq("invalidations not scheduled for another cycle" -> nextInvalidations)
      )
    else
      section(
        s"Cycle $cycleNum outcome",
        Nil,
        Some(if (isFullCompilation) "full compilation complete" else "no further invalidations")
      )

  def formatUsedName(usedName: UsedName): String = {
    val nonDefaultScopes = usedName.scopes.asScala.iterator
      .filterNot(_ == UseScope.Default)
      .map(_.toString)
      .toVector
      .sorted
    if (nonDefaultScopes.isEmpty) usedName.name
    else s"${usedName.name} [${nonDefaultScopes.mkString(", ")}]"
  }

  def formatUsedNames(usedNames: Iterable[UsedName]): Vector[String] =
    usedNames.iterator.map(formatUsedName).toVector.sorted

  def formatApiChange(change: APIChange): String = change match {
    case NamesChange(modifiedClass, modifiedNames) =>
      val names = modifiedNames.names.iterator.map(formatUsedName).toVector.sorted.mkString(", ")
      s"$modifiedClass: modified names $names"
    case APIChangeDueToMacroDefinition(modifiedClass) =>
      s"$modifiedClass: macro definition changed"
    case APIChangeDueToAnnotationDefinition(modifiedClass) =>
      s"$modifiedClass: annotation definition changed"
    case TraitPrivateMembersModified(modifiedClass) =>
      s"$modifiedClass: private members changed"
  }
}
