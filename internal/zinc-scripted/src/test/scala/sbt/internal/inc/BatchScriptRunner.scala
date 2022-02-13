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

package sbt.internal.inc

import scala.collection.mutable
import scala.util.{ Failure, Success, Try }
import scala.util.control.Exception

import sbt.internal.scripted._
import sbt.internal.inc.BatchScriptRunner.{ PreciseScriptedError, States }

/** Defines an alternative script runner that allows batch execution. */
private[sbt] class BatchScriptRunner extends ScriptRunner {

  /**
   * Defines a method to run batched execution.
   *
   * @param statements The list of handlers and statements.
   * @param states The states of the runner. In case it's empty, inherited apply is called.
   */
  def run(statements: List[(StatementHandler, Statement)], states: States): Unit = {
    if (states.isEmpty) apply(statements)
    else statements.foreach { case (handler, st) => processStatement(handler, st, states) }
  }

  def initStates(states: States, handlers: Seq[StatementHandler]): Unit =
    handlers.foreach(handler => states(handler) = handler.initialState)

  def cleanUpHandlers(handlers: Seq[StatementHandler], states: States): Unit = {
    for (handler <- handlers; state <- states.get(handler))
      Exception.allCatch(handler.finish(state.asInstanceOf[handler.State]))
  }

  def processStatement(handler: StatementHandler, st: Statement, states: States): Unit = {
    val state = states(handler).asInstanceOf[handler.State]
    Try(handler(st.command, st.arguments, state)) match {
      case Success(s) if st.successExpected => states(handler) = s
      case Success(_)                       => throw new PreciseScriptedError(st, "Expecting error at", null)
      case Failure(t: TestFailed) if st.successExpected =>
        throw new PreciseScriptedError(st, s"${t.getMessage} produced by", null)
      case Failure(err) if st.successExpected =>
        throw new PreciseScriptedError(st, "Command failed", err)
      case Failure(_) =>
    }
  }
}

private[sbt] object BatchScriptRunner {
  type States = mutable.HashMap[StatementHandler, Any]

  // Should be used instead of sbt.internal.scripted.TestException that doesn't show failed command
  final class PreciseScriptedError(st: Statement, msg: String, e: Throwable)
      extends RuntimeException(
        {
          val args = if (st.arguments.nonEmpty) st.arguments.mkString(" ", " ", "") else ""
          s"${st.linePrefix}$msg: '${st.command}$args'"
        },
        e,
        false,
        false
      )
}
