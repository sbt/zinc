/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import org.scalatest._
import sbt.util.{ Logger, LogExchange, Level }
import sbt.internal.util.{ ManagedLogger, ConsoleOut, MainAppender }
import java.util.concurrent.atomic.AtomicInteger

abstract class UnitSpec extends FlatSpec with Matchers {
  lazy val log: ManagedLogger = UnitSpec.newLogger
}

object UnitSpec {
  val console = ConsoleOut.systemOut
  val consoleAppender = MainAppender.defaultScreen(console)
  val generateId: AtomicInteger = new AtomicInteger
  def newLogger: ManagedLogger = {
    val loggerName = "test-" + generateId.incrementAndGet
    val x = LogExchange.logger(loggerName)
    LogExchange.unbindLoggerAppenders(loggerName)
    LogExchange.bindLoggerAppenders(loggerName, (consoleAppender -> Level.Debug) :: Nil)
    x
  }
}
