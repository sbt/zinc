/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc

// The following code is based on scala.tools.nsc.reporters.{AbstractReporter, ConsoleReporter, Reporter}
// Copyright 2002-2009 LAMP/EPFL
// see licenses/LICENSE_Scala
// Original author: Martin Odersky

import xsbti.{ Logger, Position, Problem, Reporter, Severity }
import java.util.EnumMap

import scala.collection.mutable
import LoggedReporter._
import sbt.internal.util.ManagedLogger
import sbt.internal.util.codec._
import Severity.{ Error, Warn, Info => SInfo }

object LoggedReporter {
  final class PositionKey(pos: Position) {
    import sbt.util.InterfaceUtil.jo2o
    def offset = pos.offset
    def sourceFile = pos.sourceFile

    override def equals(o: Any) =
      o match { case pk: PositionKey => equalsKey(pk); case _ => false }

    def equalsKey(o: PositionKey) =
      jo2o(pos.offset) == jo2o(o.offset) &&
        jo2o(pos.sourceFile) == jo2o(o.sourceFile)
    override def hashCode =
      jo2o(pos.offset).hashCode * 31 + jo2o(pos.sourceFile).hashCode
  }

  def countElementsAsString(n: Int, elements: String): String = {
    n match {
      case 0 => "no " + elements + "s"
      case 1 => "one " + elements
      case 2 => "two " + elements + "s"
      case 3 => "three " + elements + "s"
      case 4 => "four " + elements + "s"
      case _ => "" + n + " " + elements + "s"
    }
  }

  lazy val problemFormats: ProblemFormats = new ProblemFormats with SeverityFormats
  with PositionFormats with sjsonnew.BasicJsonProtocol {}
  lazy val problemStringFormats: ProblemStringFormats = new ProblemStringFormats {}
}

/**
 * Defines a logger that uses event logging provided by a ManagedLogger.
 *
 * This functionality can be use by anyone that wants to get support for event
 * logging and use an underlying, controlled logger under the hood.
 *
 * The [[ManagedLoggedReporter]] exists for those users that do not want to set
 * up the passed logger. Event logging requires registration of codects to
 * serialize and deserialize `Problem`s. This reporter makes sure to initialize
 * the managed logger so that users do not need to take care of this cumbersome process.
 *
 * @param maximumErrors The maximum errors.
 * @param logger The event managed logger.
 * @param sourcePositionMapper The position mapper.
 */
class ManagedLoggedReporter(
    maximumErrors: Int,
    logger: ManagedLogger,
    sourcePositionMapper: Position => Position = identity[Position]
) extends LoggedReporter(maximumErrors, logger, sourcePositionMapper) {
  import problemFormats._
  import problemStringFormats._
  logger.registerStringCodec[Problem]

  override def logError(problem: Problem): Unit = logger.errorEvent(problem)
  override def logWarning(problem: Problem): Unit = logger.warnEvent(problem)
  override def logInfo(problem: Problem): Unit = logger.infoEvent(problem)
}

/**
 * Defines a reporter that forwards every reported problem to a wrapped logger.
 *
 * This is the most common use of a reporter, where users pass in whichever logger
 * they want. If they are depending on loggers from other libraries, they can
 * create a logger that extends the xsbti logging interface.
 *
 * @param maximumErrors The maximum errors.
 * @param logger The logger interface provided by the user.
 * @param sourcePositionMapper The position mapper.
 */
class LoggedReporter(
    maximumErrors: Int,
    logger: Logger,
    sourcePositionMapper: Position => Position = identity[Position]
) extends Reporter {
  import sbt.util.InterfaceUtil.{ toSupplier => f0 }
  lazy val positions = new mutable.HashMap[PositionKey, Severity]
  lazy val count = new EnumMap[Severity, Int](classOf[Severity])
  protected lazy val allProblems = new mutable.ListBuffer[Problem]
  reset()

  def reset(): Unit = {
    count.put(Warn, 0)
    count.put(SInfo, 0)
    count.put(Error, 0)
    positions.clear()
    allProblems.clear()
  }

  def hasWarnings = count.get(Warn) > 0
  def hasErrors = count.get(Error) > 0
  def problems: Array[Problem] = allProblems.toArray
  def comment(pos: Position, msg: String): Unit = ()

  override def log(problem0: Problem): Unit = {
    import sbt.util.InterfaceUtil
    val (category, position, message, severity, rendered) =
      (problem0.category, problem0.position, problem0.message, problem0.severity, problem0.rendered)
    // Note: positions in reported errors can be fixed with `sourcePositionMapper`.
    val transformedPos: Position = sourcePositionMapper(position)
    val problem = InterfaceUtil.problem(
      category,
      transformedPos,
      message,
      severity,
      InterfaceUtil.jo2o(rendered)
    )
    allProblems += problem
    severity match {
      case Warn | Error =>
        if (!testAndLog(transformedPos, severity)) display(problem)
        else ()
      case _ => display(problem)
    }
  }

  override def printSummary(): Unit = {
    val warnings = count.get(Severity.Warn)
    if (warnings > 0)
      logger.warn(f0(countElementsAsString(warnings, "warning") + " found"))
    val errors = count.get(Severity.Error)
    if (errors > 0)
      logger.error(f0(countElementsAsString(errors, "error") + " found"))
  }

  private def inc(sev: Severity) = count.put(sev, count.get(sev) + 1)
  protected def logError(problem: Problem): Unit = logger.error(f0(problem.toString))
  protected def logWarning(problem: Problem): Unit = logger.warn(f0(problem.toString))
  protected def logInfo(problem: Problem): Unit = logger.info(f0(problem.toString))

  private def display(p: Problem): Unit = {
    val severity = p.severity()
    inc(severity)
    if (severity != Error || maximumErrors <= 0 || count.get(severity) <= maximumErrors) {
      severity match {
        case Error => logError(p)
        case Warn  => logWarning(p)
        case SInfo => logInfo(p)
      }
    }
  }

  private def testAndLog(pos: Position, severity: Severity): Boolean = {
    if (!pos.offset.isPresent || !pos.sourceFile.isPresent) false
    else {
      val key = new PositionKey(pos)
      if (positions.get(key).exists(_.ordinal >= severity.ordinal))
        true
      else {
        positions(key) = severity
        false
      }
    }
  }
}
