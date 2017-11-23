/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.nio.file.Path

import sbt.internal.util.ManagedLogger
import xsbti.{ Logger, Position, Problem, Severity }

/**
 * Defines a filtered reporter to control which messages are reported or not.
 *
 * This reporter is meant to be used with a `ManagedLogger`, which will be set up.
 * See [[ManagedLoggedReporter]] for a similar case.
 *
 * This implementation has been adapted from the Pants repository.
 * @link https://github.com/pantsbuild/pants/blob/master/src/scala/org/pantsbuild/zinc/logging/Reporters.scala#L28
 *
 * This reporter may be useful to companies that have domain-specific knowledge
 * about compile messages that are not relevant and can be filtered out, or users
 * that hold similar knowledge about the piece of code that they compile.
 */
class ManagedFilteredReporter(
    fileFilters: Array[Path => java.lang.Boolean],
    msgFilters: Array[String => java.lang.Boolean],
    maximumErrors: Int,
    logger: ManagedLogger,
    positionMapper: Position => Position
) extends FilteredReporter(fileFilters, msgFilters, maximumErrors, logger, positionMapper) {
  import LoggedReporter.problemFormats._
  import LoggedReporter.problemStringFormats._
  logger.registerStringCodec[Problem]

  override def logError(problem: Problem): Unit = logger.errorEvent(problem)
  override def logWarning(problem: Problem): Unit = logger.warnEvent(problem)
  override def logInfo(problem: Problem): Unit = logger.infoEvent(problem)
}

/**
 * Defines a filtered reporter to control which messages are reported or not.
 *
 * This implementation has been adapted from the Pants repository.
 * @link https://github.com/pantsbuild/pants/blob/master/src/scala/org/pantsbuild/zinc/logging/Reporters.scala#L28
 *
 * This reporter may be useful to companies that have domain-specific knowledge
 * about compile messages that are not relevant and can be filtered out, or users
 * that hold similar knowledge about the piece of code that they compile.
 */
class FilteredReporter(
    fileFilters: Array[Path => java.lang.Boolean],
    msgFilters: Array[String => java.lang.Boolean],
    maximumErrors: Int,
    logger: Logger,
    positionMapper: Position => Position
) extends LoggedReporter(maximumErrors, logger, positionMapper) {
  private final def isFiltered(pos: Position, msg: String, severity: Severity): Boolean = {
    def isFiltered[T](filters: Seq[T => java.lang.Boolean], value: T): Boolean =
      filters.exists(f => f(value).booleanValue())

    severity != Severity.Error && (
      (pos.sourceFile.isPresent && isFiltered(fileFilters, pos.sourceFile.get.toPath)) ||
      (isFiltered(msgFilters, msg))
    )
  }

  /**
   * Redefines display so that non-error messages are filtered.
   *
   * Problems are filtered out when they happen in a file that matches the regex in `fileFilters`
   * or when the content of the messages contain `msgFilters`.
   *
   * Problems that are filtered are not logged with the underlying logger but they are still
   * registered as problems so that users of `problems()` receive them.
   */
  override def log(problem: Problem): Unit = {
    val (category, position, message, severity) =
      (problem.category, problem.position, problem.message, problem.severity)
    val dontShow = isFiltered(position, message, severity)
    if (!dontShow) super.log(problem)
    else {
      // Even if we don't display, we do want to register the problem
      import sbt.util.InterfaceUtil
      val transformedPos: Position = positionMapper(position)
      val problem = InterfaceUtil.problem(category, transformedPos, message, severity)
      allProblems += problem
    }
  }
}
