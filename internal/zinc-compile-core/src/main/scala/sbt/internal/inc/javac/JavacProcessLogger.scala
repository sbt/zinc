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
package javac

import xsbti._
import java.io.File

import scala.collection.mutable.ListBuffer
import scala.sys.process.ProcessLogger

/**
 * An adapted process logger which can feed semantic error events from Javac as well as just
 * dump logs.
 *
 * @param log  The logger where all non-semantic messages will go.
 * @param reporter  A reporter for semantic Javac error messages.
 * @param cwd The current working directory of the Javac process, used when parsing Filenames.
 */
final class JavacLogger(log: sbt.util.Logger, reporter: Reporter, cwd: File) extends ProcessLogger {
  private val out: ListBuffer[String] = new ListBuffer()
  private val err: ListBuffer[String] = new ListBuffer()

  def out(s: => String): Unit =
    synchronized {
      out += s
      ()
    }

  def err(s: => String): Unit =
    synchronized {
      err += s
      ()
    }

  def buffer[T](f: => T): T = f

  def flush(exitCode: Int): Unit = flush("tool", exitCode)

  def flush(toolname: String, exitCode: Int): Unit = {
    // TODO - NOTES may not be displayed correctly!
    synchronized {
      val parser = new JavaErrorParser(cwd)

      parser.parseProblems(err.mkString("\n"), log).foreach(reporter.log(_))
      out.foreach(log.info(_))

      if (exitCode != 0)
        log.warn(s"$toolname exited with exit code $exitCode")

      out.clear()
      err.clear()
    }
  }
}
