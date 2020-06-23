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

import java.nio.file.{ Files, Path }
import java.util.concurrent.atomic.AtomicInteger

import sbt.internal.scripted._
import sbt.io.syntax._
import sbt.io.IO
import sbt.io.FileFilter._
import sbt.internal.io.Resources
import sbt.internal.util.BufferedAppender
import sbt.internal.util.{ ConsoleAppender, ConsoleOut, ManagedLogger, TraceEvent }
import sbt.util.{ Level, LogExchange }

final class ScriptedTests(
    resourceBaseDirectory: Path,
    bufferLog: Boolean,
    outLevel: Level.Value,
    handlersProvider: HandlersProvider,
    logsDir: Path
) {
  import ScriptedTests._

  private[this] val batchIdGenerator: AtomicInteger = new AtomicInteger
  private[this] val runIdGenerator: AtomicInteger = new AtomicInteger

  final val ScriptFilename = "test"
  final val PendingScriptFilename = "pending"
  private val testResources = new Resources(resourceBaseDirectory.toFile)

  private def createScriptedHandlers(
      label: String,
      testDir: Path,
      logger: ManagedLogger
  ): Map[Char, StatementHandler] = {
    val scriptConfig = new ScriptConfig(label, testDir.toFile, logger)
    handlersProvider.getHandlers(scriptConfig)
  }

  /** Returns a sequence of test runners that have to be applied in the call site. */
  def batchScriptedRunner(tests: Seq[ScriptedTest], instances: Int): Seq[TestRunner] = {
    // Test group and names may be file filters (like '*')
    val groupAndNameDirs = for {
      ScriptedTest(group, name) <- tests
      groupDir <- resourceBaseDirectory.toFile.glob(group).get.map(_.toPath)
      testDir <- groupDir.toFile.*(name).get.map(_.toPath)
    } yield (groupDir, testDir)

    val labelsAndDirs = groupAndNameDirs.map {
      case (groupDir, nameDir) =>
        val groupName = groupDir.getFileName.toString
        val testName = nameDir.getFileName.toString
        val testDirectory = testResources.readOnlyResourceDirectory(groupName, testName)
        (groupName, testName) -> testDirectory
    }

    if (labelsAndDirs.isEmpty) List()
    else {
      val batchSeed = labelsAndDirs.size / instances
      val batchSize = if (batchSeed == 0) labelsAndDirs.size else batchSeed
      labelsAndDirs
        .grouped(batchSize)
        .map { batch => () =>
          IO.withTemporaryDirectory(tempDir => runBatchedTests(batch, tempDir.toPath))
        }
        .toList
    }
  }

  def createScriptedLogFile(loggerName: String): Path = {
    val name = s"$loggerName-${runIdGenerator.incrementAndGet}.log"
    val logFile = logsDir.resolve(name)
    if (!Files.exists(logFile)) {
      Files.createFile(logFile)
    }
    logFile
  }

  case class ScriptedLogger(log: ManagedLogger, buffer: BufferedAppender)

  private val BufferSize = 8192 // copied from IO since it's private

  def rebindLogger(logger: ScriptedLogger, logFile: Path): ScriptedLogger = {
    // Create buffered logger to a file that we will afterwards use.
    import java.io.{ BufferedWriter, FileWriter }
    val name = logger.log.name
    val writer = new BufferedWriter(new FileWriter(logFile.toFile), BufferSize)
    val fileOut = ConsoleOut.bufferedWriterOut(writer)
    val fileAppender = ConsoleAppender(name, fileOut, useFormat = false)
    LogExchange.bindLoggerAppenders(name, List(fileAppender -> Level.Debug))
    logger
  }

  private def createBatchLogger(name: String): ScriptedLogger = {
    val logger = LogExchange.logger(name)
    val outAppender = BufferedAppender(ConsoleAppender())
    LogExchange.unbindLoggerAppenders(name)
    LogExchange.bindLoggerAppenders(name, List(outAppender -> outLevel))
    ScriptedLogger(logger, outAppender)
  }

  /** Defines the batch execution of scripted tests.
   *
   * Scripted tests are run one after the other one recycling the handlers, under
   * the assumption that handlers do not produce side effects that can change scripted
   * tests' behaviours.
   *
   * In batch mode, the test runner performs these operations between executions:
   *
   * 1. Delete previous test files in the common test directory.
   * 2. Copy over next test files to the common test directory.
   * 3. Reload the sbt handler.
   *
   * @param groupedTests The labels and directories of the tests to run.
   * @param batchTmpDir The common test directory.
   */
  private def runBatchedTests(
      groupedTests: Seq[((String, String), File)],
      batchTmpDir: Path
  ): Seq[Option[String]] = {
    val runner = new BatchScriptRunner
    val batchId = s"initial-batch-${batchIdGenerator.incrementAndGet()}"
    val batchLogger = createBatchLogger(batchId)
    if (bufferLog) batchLogger.buffer.record()
    val handlers = createScriptedHandlers(batchId, batchTmpDir, batchLogger.log)
    val states = new BatchScriptRunner.States
    val seqHandlers = handlers.values.toList
    runner.initStates(states, seqHandlers)

    try groupedTests.map {
      case ((group, name), originalDir) =>
        val label = s"$group/$name"
        val loggerName = s"scripted-$group-$name.log"
        val logFile = createScriptedLogFile(loggerName)
        val logger = rebindLogger(batchLogger, logFile)
        if (bufferLog) batchLogger.buffer.record()

        batchLogger.log.info(s"Running $label")
        // Copy test's contents
        IO.copyDirectory(originalDir, batchTmpDir.toFile)

        // Reset the state of `IncHandler` between every scripted run
        runner.cleanUpHandlers(seqHandlers, states)
        runner.initStates(states, seqHandlers)

        // Run the test and delete files (except global that holds local scala jars)
        val runTest = () => commonRunTest(label, batchTmpDir, handlers, runner, states, logger)
        val result = runOrHandleDisabled(label, batchTmpDir, runTest, logger)
        IO.delete(batchTmpDir.toFile.*("*" -- "global").get)
        result
    } finally runner.cleanUpHandlers(seqHandlers, states)
  }

  private def runOrHandleDisabled(
      label: String,
      testDirectory: Path,
      runTest: () => Option[String],
      logger: ScriptedLogger
  ): Option[String] = {
    val existsDisabled = Files.isRegularFile(testDirectory.resolve("disabled"))
    if (existsDisabled) {
      logger.log.warn(s"${Console.YELLOW}${Console.BOLD}D${Console.RESET} $label [DISABLED]")
      None
    } else runTest()
  }

  private def commonRunTest(
      label: String,
      testDirectory: Path,
      handlers: Map[Char, StatementHandler],
      runner: BatchScriptRunner,
      states: BatchScriptRunner.States,
      scriptedLogger: ScriptedLogger
  ): Option[String] = {
    val ScriptedLogger(logger, buffer) = scriptedLogger

    val (file, pending) = {
      val normal = testDirectory.resolve(ScriptFilename)
      val pending = testDirectory.resolve(PendingScriptFilename)
      if (Files.isRegularFile(pending)) (pending, true)
      else (normal, false)
    }

    def testFailed(t: Throwable): Option[String] = {
      if (pending) {
        import sbt.internal.util.codec.JsonProtocol._
        // Use trace but in debug mode (default trace in `ManagedLogger` prints at the error level)
        logger.logEvent(Level.Debug, TraceEvent("Debug", t, logger.channelName, logger.execId))
        buffer.clearBuffer()

        logger.warn(s"Pending cause: '${t.getMessage}'")
        logger.warn(s"${Console.YELLOW}${Console.BOLD}x${Console.RESET} $label [PENDING]")
        None
      } else {
        logger.error(s"${Console.RED}${Console.BOLD}x${Console.RESET} $label")
        logger.trace(t)
        Some(label)
      }
    }

    import scala.util.control.Exception.catching
    catching(classOf[BatchScriptRunner.PreciseScriptedError])
      .withApply(testFailed)
      .andFinally(buffer.stopBuffer())
      .apply {
        val parser = new TestScriptParser(handlers)
        val handlersAndStatements = parser.parse(file.toFile)
        runner.run(handlersAndStatements, states)

        // Handle successful tests
        if (bufferLog) buffer.clearBuffer()
        if (pending) {
          logger.info(s"${Console.RED}${Console.BOLD}+${Console.RESET} $label [PENDING]")
          logger.error(s" -> Pending test $label passed. Mark as passing to remove this failure.")
          Some(label)
        } else {
          logger.info(s"${Console.GREEN}${Console.BOLD}+${Console.RESET} $label")
          None
        }
      }
  }
}

object ScriptedTests {
  type TestRunner = () => Seq[Option[String]]
  val emptyCallback: Path => Unit = _ => ()
}
