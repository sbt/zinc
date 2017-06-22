package sbt.internal.inc

import xsbti.{ Logger, Position, Problem, Severity }

import scala.util.matching.Regex

/**
 * Defines a filtered reporter as Pants Zinc's fork does letting users control which messages
 * are reported or not. This implementation has been adapted from the Pants repository.
 *
 * @link https://github.com/pantsbuild/pants/blob/master/src/scala/org/pantsbuild/zinc/logging/Reporters.scala#L28
 *
 * This reporter may be useful to companies that have domain-specific knowledge
 * about compile messages that are not relevant and can be filtered out, or users
 * that hold similar knowledge about the piece of code that they compile.
 */
class FilteredReporter(
    fileFilters: Array[Regex],
    msgFilters: Array[Regex],
    maximumErrors: Int,
    logger: Logger,
    positionMapper: Position => Position
) extends LoggedReporter(maximumErrors, logger, positionMapper) {
  private final def isFiltered(pos: Position, msg: String, severity: Severity): Boolean = {
    def isFiltered(filters: Seq[Regex], str: String): Boolean =
      filters.exists(_.findFirstIn(str).isDefined)

    severity != Severity.Error && (
      (pos.sourceFile.isPresent && isFiltered(fileFilters, pos.sourceFile.get.getPath)) ||
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
