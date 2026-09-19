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

import sbt.internal.scripted.*
import sbt.io.syntax.*
import sbt.io.IO
import sbt.io.FileFilter.*
import sbt.internal.io.Resources
import sbt.internal.util.BufferedAppender
import sbt.internal.util.{ ConsoleAppender, ConsoleOut, ManagedLogger, TraceEvent }
import sbt.util.{ Level, LoggerContext }

final class ScriptedTests(
    resourceBaseDirectory: Path,
    bufferLog: Boolean,
    outLevel: Level.Value,
    handlersProvider: HandlersProvider,
    logsDir: Path
):
  import ScriptedTests.*

  private val batchIdGenerator: AtomicInteger = new AtomicInteger
  private val runIdGenerator: AtomicInteger = new AtomicInteger

  final val ScriptFilename = "test"
  final val PendingScriptFilename = "pending"
  private val testResources = new Resources(resourceBaseDirectory.toFile)

  private def createScriptedHandlers(
      label: String,
      testDir: Path,
      logger: ManagedLogger
  ): Map[Char, StatementHandler] =
    val scriptConfig = new ScriptConfig(label, testDir.toFile, logger)
    handlersProvider.getHandlers(scriptConfig)

  /** Returns a sequence of test runners that have to be applied in the call site. */
  def batchScriptedRunner(tests: Seq[ScriptedTest], instances: Int): Seq[TestRunner] =
    // Test group and names may be file filters (like '*')
    val groupAndNameDirs =
      for
        ScriptedTest(group, name) <- tests
        groupDir <- resourceBaseDirectory.toFile.glob(group).get().map(_.toPath)
        testDir <- groupDir.toFile.*(name).get().map(_.toPath)
      yield (groupDir, testDir)

    val labelsAndDirs = groupAndNameDirs.map {
      case (groupDir, nameDir) =>
        val groupName = groupDir.getFileName.toString
        val testName = nameDir.getFileName.toString
        val testDirectory = testResources.readOnlyResourceDirectory(groupName, testName)
        (groupName, testName) -> testDirectory
    }

    if labelsAndDirs.isEmpty then List()
    else
      val batchSeed = labelsAndDirs.size / instances
      val batchSize = if batchSeed == 0 then labelsAndDirs.size else batchSeed
      labelsAndDirs
        .grouped(batchSize)
        .map { batch => () =>
          IO.withTemporaryDirectory(tempDir => runBatchedTests(batch, tempDir.toPath))
        }
        .toList
  end batchScriptedRunner

  def createScriptedLogFile(loggerName: String): Path =
    val name = s"$loggerName-${runIdGenerator.incrementAndGet}.log"
    val logFile = logsDir.resolve(name)
    if !Files.exists(logFile) then
      Files.createFile(logFile)
    logFile

  case class ScriptedLogger(log: ManagedLogger, buffer: BufferedAppender)

  private val BufferSize = 8192 // copied from IO since it's private

  def rebindLogger(logger: ScriptedLogger, logFile: Path): ScriptedLogger =
    // Create buffered logger to a file that we will afterwards use.
    import java.io.{ BufferedWriter, FileWriter }
    val name = logger.log.name
    val writer = new BufferedWriter(new FileWriter(logFile.toFile), BufferSize)
    val fileOut = ConsoleOut.bufferedWriterOut(writer)
    val fileAppender = ConsoleAppender(name, fileOut, useFormat = false)
    LoggerContext.globalContext.addAppender(name, fileAppender -> Level.Debug)
    logger

  private def createBatchLogger(name: String): ScriptedLogger =
    val logger = LoggerContext.globalContext.logger(name, None, None)
    val outAppender = BufferedAppender(ConsoleAppender())
    LoggerContext.globalContext.clearAppenders(name)
    LoggerContext.globalContext.addAppender(name, outAppender -> outLevel)
    ScriptedLogger(logger, outAppender)

  /**
   * Runs the tests of a batch one after the other, each in a fresh directory with fresh handlers.
   *
   * Sharing one directory and clearing it between tests is not isolation: on Windows a file that
   * is still open cannot be deleted, and a JAR left behind under `lib/` lands on the next test's
   * classpath.
   *
   * @param groupedTests The labels and directories of the tests to run.
   * @param batchTmpDir The directory holding one subdirectory per test.
   */
  private def runBatchedTests(
      groupedTests: Seq[((String, String), File)],
      batchTmpDir: Path
  ): Seq[Option[String]] =
    val runner = new BatchScriptRunner
    val batchId = s"initial-batch-${batchIdGenerator.incrementAndGet()}"
    val batchLogger = createBatchLogger(batchId)
    if bufferLog then batchLogger.buffer.record()

    groupedTests.zipWithIndex.map {
      case (((group, name), originalDir), index) =>
        val label = s"$group/$name"
        val loggerName = s"scripted-$group-$name.log"
        val logFile = createScriptedLogFile(loggerName)
        val logger = rebindLogger(batchLogger, logFile)
        if bufferLog then batchLogger.buffer.record()

        batchLogger.log.info(s"Running $label")
        val testDir = Files.createDirectory(batchTmpDir.resolve(index.toString))
        IO.copyDirectory(originalDir, testDir.toFile)

        val handlers = createScriptedHandlers(batchId, testDir, batchLogger.log)
        val states = new BatchScriptRunner.States
        val seqHandlers = handlers.values.toList
        runner.initStates(states, seqHandlers)
        try
          val runTest = () => commonRunTest(label, testDir, handlers, runner, states, logger)
          runOrHandleDisabled(label, testDir, runTest, logger)
        finally
          runner.cleanUpHandlers(seqHandlers, states)
          IO.delete(testDir.toFile)
    }
  end runBatchedTests

  private def runOrHandleDisabled(
      label: String,
      testDirectory: Path,
      runTest: () => Option[String],
      logger: ScriptedLogger
  ): Option[String] =
    val existsDisabled = Files.isRegularFile(testDirectory.resolve("disabled"))
    if existsDisabled then
      logger.log.warn(s"${Console.YELLOW}${Console.BOLD}D${Console.RESET} $label [DISABLED]")
      None
    else runTest()

  private def commonRunTest(
      label: String,
      testDirectory: Path,
      handlers: Map[Char, StatementHandler],
      runner: BatchScriptRunner,
      states: BatchScriptRunner.States,
      scriptedLogger: ScriptedLogger
  ): Option[String] =
    val ScriptedLogger(logger, buffer) = scriptedLogger

    val (file, pending) =
      val normal = testDirectory.resolve(ScriptFilename)
      val pending = testDirectory.resolve(PendingScriptFilename)
      if Files.isRegularFile(pending) then (pending, true)
      else (normal, false)

    def testFailed(t: Throwable): Option[String] =
      if pending then
        import sbt.internal.util.codec.JsonProtocol.given
        // Use trace but in debug mode (default trace in `ManagedLogger` prints at the error level)
        logger.logEvent(Level.Debug, TraceEvent("Debug", t, logger.channelName, logger.execId))
        buffer.clearBuffer()

        logger.warn(s"Pending cause: '${t.getMessage}'")
        logger.warn(s"${Console.YELLOW}${Console.BOLD}x${Console.RESET} $label [PENDING]")
        None
      else
        logger.error(s"${Console.RED}${Console.BOLD}x${Console.RESET} $label")
        logger.trace(t)
        Some(label)

    import scala.util.control.Exception.catching
    catching(classOf[BatchScriptRunner.PreciseScriptedError])
      .withApply(testFailed)
      .andFinally(buffer.stopBuffer())
      .apply {
        val parser = new TestScriptParser(handlers)
        val handlersAndStatements = parser.parse(file.toFile, false)
        runner.run(handlersAndStatements, states)

        // Handle successful tests
        if bufferLog then buffer.clearBuffer()
        if pending then
          logger.info(s"${Console.RED}${Console.BOLD}+${Console.RESET} $label [PENDING]")
          logger.error(s" -> Pending test $label passed. Mark as passing to remove this failure.")
          Some(label)
        else
          logger.info(s"${Console.GREEN}${Console.BOLD}+${Console.RESET} $label")
          None
      }
  end commonRunTest
end ScriptedTests

object ScriptedTests:
  type TestRunner = () => Seq[Option[String]]
  val emptyCallback: Path => Unit = _ => ()
