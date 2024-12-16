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

package sbt.internal.inc.javac

import sbt.util.{ Level, Logger }

class CollectingLogger extends Logger {
  var messages: Map[Level.Value, Seq[String]] = Map.empty.withDefaultValue(Seq.empty)

  override def trace(t: => Throwable): Unit = ???
  override def success(message: => String): Unit = ???
  override def log(level: Level.Value, message: => String): Unit =
    synchronized {
      messages = messages.updated(level, messages(level) :+ message)
    }
}
