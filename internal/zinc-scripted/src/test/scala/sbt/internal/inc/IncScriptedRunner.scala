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

import java.nio.file.Path

import sbt.internal.scripted.{ HandlersProvider, ListTests, ScriptedTest }
import sbt.io.syntax._
import sbt.io.IO
import sbt.util.{ Level, Logger }

import scala.collection.parallel.ParSeq

object ScriptedRunnerImpl {
  type TestRunner = () => Seq[Option[String]]
  private[this] val random = new java.util.Random()

  def run(
      baseDir: Path,
      bufferLog: Boolean,
      tests: Seq[ScriptedTest],
      handlersProvider: HandlersProvider,
      instances: Int
  ): Unit = {
    val globalLogger = sbt.internal.util.ConsoleLogger()
    val logsDir = IO.temporaryDirectory / s"scripted-logs-${Integer.toHexString(random.nextInt())}"
    IO.createDirectory(logsDir)
    val outLevel = Level.Debug // if (bufferLog) Level.Info else Level.Debug
    val runner = new ScriptedTests(baseDir, bufferLog, outLevel, handlersProvider, logsDir.toPath)
    val scriptedTests = if (tests.isEmpty) listTests(baseDir, globalLogger) else tests
    val scriptedRunners = runner.batchScriptedRunner(scriptedTests, instances)
    val parallelRunners = scriptedRunners.toParArray
    val pool = new java.util.concurrent.ForkJoinPool(instances)
    parallelRunners.tasksupport = new scala.collection.parallel.ForkJoinTaskSupport(pool)
    try runAllInParallel(parallelRunners, scriptedTests.size)
    finally globalLogger.info(s"Log files of all scripted tests run: ${logsDir.absolutePath}")
  }

  def runAllInParallel(tests: ParSeq[TestRunner], size: Int): Unit = {
    def reportErrors(tests: Seq[String]): Unit = {
      if (tests.nonEmpty) {
        val msg =
          s"""${tests.size} (out of $size) scripted tests failed:
             |${tests.mkString(s"${IO.Newline}\t", s"${IO.Newline}\t", IO.Newline)}""".stripMargin
        object ScriptedFailure extends RuntimeException(msg, null, false, false)
        throw ScriptedFailure
      }
    }

    reportErrors(tests.flatMap(test => test.apply()).flatten.toList)
  }

  def listTests(baseDir: Path, log: Logger): Seq[ScriptedTest] =
    new ListTests(baseDir.toFile, _ => true, log).listTests
}
