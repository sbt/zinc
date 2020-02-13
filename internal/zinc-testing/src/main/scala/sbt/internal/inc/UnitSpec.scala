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

import org.scalatest._
import sbt.util.{ LogExchange, Level }
import sbt.internal.util.{ ManagedLogger, ConsoleOut, MainAppender }
import java.util.concurrent.atomic.AtomicInteger

abstract class UnitSpec extends FlatSpec with Matchers {
  def logLevel: Level.Value = Level.Warn
  lazy val log: ManagedLogger = UnitSpec.newLogger(logLevel)
}

object UnitSpec {
  val console = ConsoleOut.systemOut
  val consoleAppender = MainAppender.defaultScreen(console)
  val generateId: AtomicInteger = new AtomicInteger
  def newLogger(level: Level.Value): ManagedLogger = {
    val loggerName = "test-" + generateId.incrementAndGet
    val x = LogExchange.logger(loggerName)
    LogExchange.unbindLoggerAppenders(loggerName)
    LogExchange.bindLoggerAppenders(loggerName, (consoleAppender -> level) :: Nil)
    x
  }
}
