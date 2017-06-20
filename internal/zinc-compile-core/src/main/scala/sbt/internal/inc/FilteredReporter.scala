package sbt.internal.inc

import sbt.internal.util.ManagedLogger
import xsbti.{ Position, Problem, Severity }

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
    logger: ManagedLogger,
    positionMapper: Position => Position
) extends LoggerReporter(maximumErrors, logger, positionMapper) {
  private final def isFiltered(filters: Seq[Regex], str: String): Boolean =
    filters.exists(_.findFirstIn(str).isDefined)

  private final def isFiltered(pos: Position, msg: String, severity: Severity): Boolean = {
    severity != Severity.Error && (
      (pos.sourceFile.isPresent && isFiltered(fileFilters, pos.sourceFile.get.getPath)) ||
      (isFiltered(msgFilters, msg))
    )
  }

  /**
   * Redefines display so that non-error messages whose paths match a regex in `fileFilters`
   * or whose messages' content match `msgFilters` are not reported to the user.
   */
  override def display(problem: Problem): Unit = {
    val severity = problem.severity()
    val dontShow = isFiltered(problem.position(), problem.message(), severity)
    if (dontShow) inc(severity)
    else super.display(problem)
  }
}
