/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File

import sbt.internal.scripted._
import sbt.internal.util.ManagedLogger

class SleepingHandler(val handler: StatementHandler, delay: Long) extends StatementHandler {
  type State = handler.State
  override def initialState: State = handler.initialState
  override def apply(command: String, arguments: List[String], state: State): State = {
    val result = handler.apply(command, arguments, state)
    Thread.sleep(delay)
    result
  }
  override def finish(state: State) = handler.finish(state)
}

class IncScriptedHandlers(globalCacheDir: File) extends HandlersProvider {
  def getHandlers(config: ScriptConfig): Map[Char, StatementHandler] = Map(
    '$' -> new SleepingHandler(new FileCommands(config.testDirectory()), 500),
    '#' -> CommentHandler,
    '>' -> {
      val logger: ManagedLogger =
        config.logger() match {
          case x: ManagedLogger => x
          case _                => sys.error("Expected ManagedLogger")
        }
      new IncHandler(config.testDirectory(), globalCacheDir, logger)
    }
  )
}
