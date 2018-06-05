package sbt.internal.inc

import org.scalatest.exceptions.TestFailedException
import sbt.internal.inc
import sbt.internal.scripted._
import sbt.internal.inc.BatchScriptRunner.States

/** Defines an alternative script runner that allows batch execution. */
private[sbt] class BatchScriptRunner extends ScriptRunner {

  /** Defines a method to run batched execution.
   *
   * @param statements The list of handlers and statements.
   * @param states The states of the runner. In case it's empty, inherited apply is called.
   */
  def apply(statements: List[(StatementHandler, Statement)], states: States): Unit = {
    if (states.isEmpty) super.apply(statements)
    else statements.foreach(st => processStatement(st._1, st._2, states))
  }

  def initStates(states: States, handlers: Seq[StatementHandler]): Unit =
    handlers.foreach(handler => states(handler) = handler.initialState)

  def cleanUpHandlers(handlers: Seq[StatementHandler], states: States): Unit = {
    for (handler <- handlers; state <- states.get(handler)) {
      try handler.finish(state.asInstanceOf[handler.State])
      catch { case _: Exception => () }
    }
  }

  import BatchScriptRunner.PreciseScriptedError
  def processStatement(handler: StatementHandler, statement: Statement, states: States): Unit = {
    val state = states(handler).asInstanceOf[handler.State]
    val nextState =
      try { Right(handler(statement.command, statement.arguments, state)) } catch {
        case e: Exception => Left(e)
      }
    nextState match {
      case Left(err) =>
        if (statement.successExpected) {
          err match {
            case t: TestFailed =>
              val errorMessage = s"${t.getMessage} produced by"
              throw new PreciseScriptedError(statement, errorMessage, null)
            case _ => throw new PreciseScriptedError(statement, "Command failed", err)
          }
        } else ()
      case Right(s) =>
        if (statement.successExpected) states(handler) = s
        else throw new PreciseScriptedError(statement, "Expecting error at", null)
    }
  }
}

private[sbt] object BatchScriptRunner {
  import scala.collection.mutable
  type States = mutable.HashMap[StatementHandler, Any]

  // Should be used instead of sbt.internal.scripted.TestException that doesn't show failed command
  final class PreciseScriptedError(st: Statement, msg: String, e: Throwable)
      extends RuntimeException(s"$msg: '${st.command} ${st.arguments.mkString(" ")}'", e) {
    override def fillInStackTrace = e
  }
}
