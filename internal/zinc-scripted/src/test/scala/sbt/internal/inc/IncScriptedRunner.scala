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

import java.io.File

import sbt.internal.scripted.{ HandlersProvider, ListTests, ScriptedTest }
import sbt.io.IO
import sbt.util.{ Level, Logger }

import scala.collection.parallel.ParSeq

object ScriptedRunnerImpl {
  type TestRunner = () => Seq[Option[String]]

  def run(
      resourceBaseDirectory: File,
      bufferLog: Boolean,
      tests: Array[String],
      handlersProvider: HandlersProvider,
      instances: Int
  ): Unit = {
    val globalLogger = newLogger
    val logsDir = newScriptedLogsDir.toPath
    val outLevel = Level.Debug // if (bufferLog) Level.Info else Level.Debug
    val runner =
      new ScriptedTests(
        resourceBaseDirectory.toPath,
        bufferLog,
        outLevel,
        handlersProvider,
        logsDir
      )
    val scriptedTests = get(tests, resourceBaseDirectory, globalLogger)
    val scriptedRunners = runner.batchScriptedRunner(scriptedTests, instances)
    val parallelRunners = scriptedRunners.toParArray
    val pool = new java.util.concurrent.ForkJoinPool(instances)
    parallelRunners.tasksupport = new scala.collection.parallel.ForkJoinTaskSupport(pool)
    runAllInParallel(parallelRunners, scriptedTests.size)
    globalLogger.info(s"Log files can be found at ${logsDir.toAbsolutePath}")
  }

  def runAllInParallel(tests: ParSeq[TestRunner], size: Int): Unit = {
    val nl = IO.Newline
    val nlt = nl + "\t"
    class ScriptedFailure(tests: Seq[String])
        extends RuntimeException(
          s"""${tests.size} (out of $size) scripted tests failed:
             |${tests.mkString(nlt, nlt, nl)}""".stripMargin
        ) {
      // We are not interested in the stack trace here, only the failing tests
      override def fillInStackTrace = this
    }
    def reportErrors(errors: Seq[String]): Unit =
      if (errors.nonEmpty) throw new ScriptedFailure(errors) else ()

    reportErrors(tests.flatMap(test => test.apply().flatten).toList)
  }

  def get(tests: Seq[String], baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    if (tests.isEmpty) listTests(baseDirectory, log) else parseTests(tests)

  def listTests(baseDirectory: File, log: Logger): Seq[ScriptedTest] =
    (new ListTests(baseDirectory, _ => true, log)).listTests

  def parseTests(in: Seq[String]): Seq[ScriptedTest] = for (testString <- in) yield {
    testString.split("/").map(_.trim) match {
      case Array(group, name) => ScriptedTest(group, name)
      case elems =>
        sys.error(s"Expected two arguments 'group/name', obtained ${elems.mkString("/")}")
    }
  }

  private[sbt] def newLogger: Logger = sbt.internal.util.ConsoleLogger()

  private[this] val random = new java.util.Random()
  private[sbt] def newScriptedLogsDir: File = {
    val randomName = "scripted-logs-" + java.lang.Integer.toHexString(random.nextInt)
    val logsDir = new File(IO.temporaryDirectory, randomName)
    IO.createDirectory(logsDir)
    logsDir
  }
}
