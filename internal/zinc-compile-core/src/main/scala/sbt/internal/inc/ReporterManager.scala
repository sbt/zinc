/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.{ OutputStreamWriter, PrintStream, PrintWriter }
import java.nio.charset.StandardCharsets
import java.util.logging.Level

import sbt.internal.util.{ ConsoleAppender, MainAppender, ManagedLogger }
import sbt.util.LogExchange
import sbt.util.{ Level => SbtLevel }
import xsbti.{ Position, Reporter, ReporterConfig }

import scala.util.matching.Regex

object ReporterManager {
  import java.util.concurrent.atomic.AtomicInteger
  private val idGenerator: AtomicInteger = new AtomicInteger
  private val DefaultName = "zinc-out"
  private def generateZincReporterId(name: String): String =
    s"$name-${idGenerator.incrementAndGet}"

  /**
   * Returns the id that is used to know what's the minimum level from which the
   * reporter should log messages from the compilers.
   *
   * It uses `java.util.logging.Level` because `sbt.util.Level` is a Scala enumeration
   * and there are lots of deficiencies in its design (as well as in some bugs in
   * scalac) that prevent its leak in the public Java API, even though it's supposed
   * to be public. For instance, I have tried to refer to it in the following ways:
   *
   * - `sbt.util.Level.Info` -> Scalac passes, Javac fails.
   * - `sbt.util.Level$.Info` -> Scalac error: type mismatch.
   * - `sbt.util.Level$.MODULE$.Info` -> Scalac fails with stable identifier required.
   *
   * Using `CompileOrder.Mixed` does not solve the issue. Therefore, we fallback
   * on `java.util.logging.Level` and we transform to `sbt.util.Level` at runtime. This
   * provides more type safety than asking users to provide the id of `sbt.util.Level`.
   *
   * TODO(someone): Don't define `sbt.util.Level` as a Scala enumeration.
   */
  private def fromJavaLogLevel(level: Level): SbtLevel.Value = {
    level match {
      case Level.INFO    => SbtLevel.Info
      case Level.WARNING => SbtLevel.Warn
      case Level.SEVERE  => SbtLevel.Error
      case Level.OFF     => sys.error("Level.OFF is not supported. Change the logging level.")
      case _             => SbtLevel.Debug
    }
  }

  private val UseColor = ConsoleAppender.formatEnabledInEnv
  private val NoPositionMapper = java.util.function.Function.identity[Position]()

  import java.util.function.{ Function => JavaFunction }
  private implicit class EnrichedJava[T, R](f: JavaFunction[T, R]) {
    def toScala: Function[T, R] = (t: T) => f.apply(t)
  }

  /** Returns sane defaults with a long tradition in sbt. */
  def getDefaultReporterConfig: ReporterConfig =
    ReporterConfig.of(DefaultName, 100, UseColor, Array(), Array(), Level.INFO, NoPositionMapper)

  def getReporter(logger: xsbti.Logger, config: ReporterConfig): Reporter = {
    val maxErrors = config.maximumErrors()
    val posMapper = config.positionMapper().toScala
    if (config.fileFilters().isEmpty && config.msgFilters.isEmpty) {
      logger match {
        case managed: ManagedLogger => new ManagedLoggedReporter(maxErrors, managed, posMapper)
        case _                      => new LoggedReporter(maxErrors, logger, posMapper)
      }
    } else {
      implicit def scalaPatterns(patterns: Array[java.util.regex.Pattern]): Array[Regex] =
        patterns.map(_.pattern().r)
      val fileFilters = config.fileFilters().map(_.toScala)
      val msgFilters = config.msgFilters().map(_.toScala)
      logger match {
        case managed: ManagedLogger =>
          new ManagedFilteredReporter(fileFilters, msgFilters, maxErrors, managed, posMapper)
        case _ => new FilteredReporter(fileFilters, msgFilters, maxErrors, logger, posMapper)
      }
    }
  }

  def getReporter(toOutput: PrintWriter, config: ReporterConfig): Reporter = {
    val printWriterToAppender = MainAppender.defaultBacked(config.useColor())
    val appender = printWriterToAppender(toOutput)
    val freshName = generateZincReporterId(config.loggerName())
    val logger = LogExchange.logger(freshName)
    val loggerName = logger.name

    LogExchange.unbindLoggerAppenders(loggerName)
    val sbtLogLevel = fromJavaLogLevel(config.logLevel())
    val toAppend = List(appender -> sbtLogLevel)
    LogExchange.bindLoggerAppenders(loggerName, toAppend)
    getReporter(logger, config)
  }

  def getReporter(toOutput: PrintStream, config: ReporterConfig): Reporter = {
    val utf8Writer = new OutputStreamWriter(toOutput, StandardCharsets.UTF_8)
    val printWriter = new PrintWriter(utf8Writer)
    getReporter(printWriter, config)
  }
}
