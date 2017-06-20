/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

// The following code is based on scala.tools.nsc.reporters.{AbstractReporter, ConsoleReporter, Reporter}
// Copyright 2002-2009 LAMP/EPFL
// see licenses/LICENSE_Scala
// Original author: Martin Odersky

import xsbti.{ Position, Problem, Severity, Reporter }
import java.util.EnumMap

import scala.collection.mutable
import LoggerReporter._
import sbt.internal.util.ManagedLogger
import sbt.internal.util.codec._
import sbt.util.InterfaceUtil.{ jo2o, problem }
import Severity.{ Error, Warn, Info => SInfo }

object LoggerReporter {
  final class PositionKey(pos: Position) {
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

  def countElementsAsString(n: Int, elements: String): String =
    n match {
      case 0 => "no " + elements + "s"
      case 1 => "one " + elements
      case 2 => "two " + elements + "s"
      case 3 => "three " + elements + "s"
      case 4 => "four " + elements + "s"
      case _ => "" + n + " " + elements + "s"
    }

  lazy val problemFormats: ProblemFormats = new ProblemFormats with SeverityFormats
  with PositionFormats with sjsonnew.BasicJsonProtocol {}
  lazy val problemStringFormats: ProblemStringFormats = new ProblemStringFormats {}
}

class LoggerReporter(
    maximumErrors: Int,
    logger: ManagedLogger,
    sourcePositionMapper: Position => Position = identity[Position]
) extends Reporter {
  val positions = new mutable.HashMap[PositionKey, Severity]
  val count = new EnumMap[Severity, Int](classOf[Severity])
  private[this] val allProblems = new mutable.ListBuffer[Problem]

  import problemStringFormats._
  logger.registerStringCodec[Problem]
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

  override def log(pos: Position, msg: String, severity: Severity): Unit = {
    val mappedPos: Position = sourcePositionMapper(pos)
    val p = problem("", mappedPos, msg, severity)
    allProblems += p
    severity match {
      case Warn | Error => {
        if (!testAndLog(mappedPos, severity))
          display(p)
      }
      case _ => display(p)
    }
  }

  def printSummary(): Unit = {
    val warnings = count.get(Severity.Warn)
    if (warnings > 0)
      logger.warn(countElementsAsString(warnings, "warning") + " found")
    val errors = count.get(Severity.Error)
    if (errors > 0)
      logger.error(countElementsAsString(errors, "error") + " found")
  }

  private def inc(sev: Severity) = count.put(sev, count.get(sev) + 1)

  // this is used by sbt
  private[sbt] def display(p: Problem): Unit = {
    import problemFormats._
    inc(p.severity)
    if (p.severity != Error || maximumErrors <= 0 || count.get(p.severity) <= maximumErrors) {
      p.severity match {
        case Error => logger.errorEvent(p)
        case Warn  => logger.warnEvent(p)
        case SInfo => logger.infoEvent(p)
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
