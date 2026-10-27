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

import java.io.{ BufferedWriter, FileWriter, Writer }
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

  /** Returns one runner per test, to be applied in the call site. */
  def scriptedRunners(tests: Seq[ScriptedTest]): Seq[TestRunner] =
    // Test group and names may be file filters (like '*')
    val groupAndNameDirs =
      for
        ScriptedTest(group, name) <- tests
        groupDir <- resourceBaseDirectory.toFile.glob(group).get().map(_.toPath)
        testDir <- groupDir.toFile.*(name).get().map(_.toPath)
      yield (groupDir, testDir)

    groupAndNameDirs.map {
      case (groupDir, nameDir) =>
        val groupName = groupDir.getFileName.toString
        val testName = nameDir.getFileName.toString
        val testDirectory = testResources.readOnlyResourceDirectory(groupName, testName)
        () =>
          IO.withTemporaryDirectory(tempDir => runTest(groupName, testName, testDirectory, tempDir))
    }
  end scriptedRunners

  def createScriptedLogFile(loggerName: String): Path =
    val name = s"$loggerName-${runIdGenerator.incrementAndGet}.log"
    val logFile = logsDir.resolve(name)
    if !Files.exists(logFile) then
      Files.createFile(logFile)
    logFile

  /**
   * A logger of one test: its output goes to the console, buffered when `bufferLog` is set, and to
   * the log file of that test alone.
   */
  final class ScriptedLogger(val log: ManagedLogger, val buffer: BufferedAppender, writer: Writer)
      extends AutoCloseable:
    def close(): Unit =
      LoggerContext.globalContext.remove(log.name)
      writer.close()

  private val BufferSize = 8192 // copied from IO since it's private

  private def createTestLogger(name: String, logFile: Path): ScriptedLogger =
    val logger = LoggerContext.globalContext.logger(name, None, None)
    val outAppender = BufferedAppender(ConsoleAppender())
    val writer = new BufferedWriter(new FileWriter(logFile.toFile), BufferSize)
    val fileAppender =
      ConsoleAppender(name, ConsoleOut.bufferedWriterOut(writer), useFormat = false)
    LoggerContext.globalContext.clearAppenders(name)
    LoggerContext.globalContext.addAppender(name, outAppender -> outLevel)
    LoggerContext.globalContext.addAppender(name, fileAppender -> Level.Debug)
    new ScriptedLogger(logger, outAppender, writer)

  /**
   * Runs one test in a fresh directory with fresh handlers.
   *
   * Sharing one directory between tests and clearing it in between is not isolation: on Windows a
   * file that is still open cannot be deleted, and a JAR left behind under `lib/` lands on the next
   * test's classpath.
   *
   * @param originalDir The directory of the test in the source tree.
   * @param testDir The directory to copy the test to and run it in.
   */
  private def runTest(
      group: String,
      name: String,
      originalDir: File,
      testDir: File
  ): Option[String] =
    val label = s"$group/$name"
    val logFile = createScriptedLogFile(s"scripted-$group-$name")
    val logger = createTestLogger(logFile.getFileName.toString, logFile)
    try
      if bufferLog then logger.buffer.record()
      logger.log.info(s"Running $label")
      IO.copyDirectory(originalDir, testDir)

      val runner = new BatchScriptRunner
      val handlers = createScriptedHandlers(label, testDir.toPath, logger.log)
      val states = new BatchScriptRunner.States
      val seqHandlers = handlers.values.toList
      runner.initStates(states, seqHandlers)
      try
        val runTest = () => commonRunTest(label, testDir.toPath, handlers, runner, states, logger)
        runOrHandleDisabled(label, testDir.toPath, runTest, logger)
      finally runner.cleanUpHandlers(seqHandlers, states)
    finally logger.close()
  end runTest

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
    val logger = scriptedLogger.log
    val buffer = scriptedLogger.buffer

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
  type TestRunner = () => Option[String]
  val emptyCallback: Path => Unit = _ => ()
