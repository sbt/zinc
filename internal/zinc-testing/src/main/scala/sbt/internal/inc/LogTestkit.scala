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

package sbt
package internal
package inc

import sbt.util.{ LoggerContext, Level }
import sbt.internal.util.{ ManagedLogger, ConsoleOut, MainAppender }
import java.util.concurrent.atomic.AtomicInteger

trait LogTestkit:
  def logLevel: Level.Value = Level.Warn
  lazy val log: ManagedLogger = LogTestkit.newLogger(logLevel)

object LogTestkit:
  val console = ConsoleOut.systemOut
  val consoleAppender = MainAppender.defaultScreen(console)
  val generateId: AtomicInteger = new AtomicInteger
  def newLogger(level: Level.Value): ManagedLogger =
    val loggerName = "test-" + generateId.incrementAndGet
    val x = LoggerContext.globalContext.logger(loggerName, None, None)
    LoggerContext.globalContext.clearAppenders(loggerName)
    LoggerContext.globalContext.addAppender(loggerName, consoleAppender -> level)
    x
end LogTestkit
