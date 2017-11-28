/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
