package sbt.internal.inc

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.impl.Log4jLogEvent
import sbt.internal.scripted._
import sbt.io.IO
import sbt.io.FileFilter._
import sbt.internal.io.Resources
import sbt.internal.util.{ ConsoleAppender, ConsoleOut, ManagedLogger, TraceEvent }
import sbt.util.{ Level, LogExchange }

final class ScriptedTests(resourceBaseDirectory: File,
                          bufferLog: Boolean,
                          handlersProvider: HandlersProvider,
                          logsDir: File) {
  import sbt.io.syntax._
  import ScriptedTests._

  private[this] val batchIdGenerator: AtomicInteger = new AtomicInteger
  private[this] val runIdGenerator: AtomicInteger = new AtomicInteger

  final val ScriptFilename = "test"
  final val PendingScriptFilename = "pending"
  private val testResources = new Resources(resourceBaseDirectory)

  private def createScriptedHandlers(
      label: String,
      testDir: File,
      logger: ManagedLogger
  ): Map[Char, StatementHandler] = {
    val scriptConfig = new ScriptConfig(label, testDir, logger)
    handlersProvider.getHandlers(scriptConfig)
  }

  /** Returns a sequence of test runners that have to be applied in the call site. */
  def batchScriptedRunner(
      testGroupAndNames: Seq[ScriptedTest],
      sbtInstances: Int
  ): Seq[TestRunner] = {
    // Test group and names may be file filters (like '*')
    val groupAndNameDirs = {
      for {
        ScriptedTest(group, name) <- testGroupAndNames
        groupDir <- resourceBaseDirectory.*(group).get
        testDir <- groupDir.*(name).get
      } yield (groupDir, testDir)
    }

    val labelsAndDirs = groupAndNameDirs.map {
      case (groupDir, nameDir) =>
        val groupName = groupDir.getName
        val testName = nameDir.getName
        val testDirectory = testResources.readOnlyResourceDirectory(groupName, testName)
        (groupName, testName) -> testDirectory
    }

    if (labelsAndDirs.isEmpty) List()
    else {
      val batchSeed = labelsAndDirs.size / sbtInstances
      val batchSize = if (batchSeed == 0) labelsAndDirs.size else batchSeed
      labelsAndDirs
        .grouped(batchSize)
        .map(batch => () => IO.withTemporaryDirectory(runBatchedTests(batch, _)))
        .toList
    }
  }

  def createScriptedLogFile(loggerName: String): File = {
    val name = s"$loggerName-${runIdGenerator.incrementAndGet}.log"
    val logFile = logsDir./(name)
    logFile.createNewFile()
    logFile
  }

  import sbt.internal.util.BufferedAppender
  case class ScriptedLogger(log: ManagedLogger, logFile: File, buffer: BufferedAppender)

  private val BufferSize = 8192 // copied from IO since it's private
  def rebindLogger(logger: ManagedLogger, logFile: File): ScriptedLogger = {
    // Create buffered logger to a file that we will afterwards use.
    import java.io.{ BufferedWriter, FileWriter }
    val name = logger.name
    val writer = new BufferedWriter(new FileWriter(logFile), BufferSize)
    val fileOut = ConsoleOut.bufferedWriterOut(writer)
    val fileAppender = ConsoleAppender(name, fileOut, useFormat = false)
    val outAppender = BufferedAppender(ConsoleAppender())
    val appenders = (fileAppender -> Level.Debug) :: (outAppender -> Level.Info) :: Nil
    LogExchange.unbindLoggerAppenders(name)
    LogExchange.bindLoggerAppenders(name, appenders)
    ScriptedLogger(logger, logFile, outAppender)
  }

  private final def createBatchLogger(name: String): ManagedLogger = LogExchange.logger(name)

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
   * @param log The logger.
   */
  private def runBatchedTests(
      groupedTests: Seq[((String, String), File)],
      batchTmpDir: File
  ): Seq[Option[String]] = {
    val runner = new BatchScriptRunner
    val batchId = s"initial-batch-${batchIdGenerator.incrementAndGet()}"
    val batchLogger = createBatchLogger(batchId)
    val handlers = createScriptedHandlers(batchId, batchTmpDir, batchLogger)
    val states = new BatchScriptRunner.States
    val seqHandlers = handlers.values.toList
    runner.initStates(states, seqHandlers)

    def runBatchTests = {
      groupedTests.map {
        case ((group, name), originalDir) =>
          val label = s"$group / $name"
          val loggerName = s"scripted-$group-$name.log"
          val logFile = createScriptedLogFile(loggerName)
          val logger = rebindLogger(batchLogger, logFile)

          println(s"Running $label")
          // Copy test's contents
          IO.copyDirectory(originalDir, batchTmpDir)

          // Reset the state of `IncHandler` between every scripted run
          runner.cleanUpHandlers(seqHandlers, states)
          runner.initStates(states, seqHandlers)

          // Run the test and delete files (except global that holds local scala jars)
          val runTest = () => commonRunTest(label, batchTmpDir, handlers, runner, states, logger)
          val result = runOrHandleDisabled(label, batchTmpDir, runTest, logger)
          IO.delete(batchTmpDir.*("*" -- "global").get)
          result
      }
    }

    try runBatchTests
    finally runner.cleanUpHandlers(seqHandlers, states)
  }

  private def runOrHandleDisabled(
      label: String,
      testDirectory: File,
      runTest: () => Option[String],
      logger: ScriptedLogger
  ): Option[String] = {
    val existsDisabled = new File(testDirectory, "disabled").isFile
    if (!existsDisabled) runTest()
    else {
      logger.log.info(s"D $label [DISABLED]")
      None
    }
  }

  import BatchScriptRunner.PreciseScriptedError
  private val SuccessMark = s"${Console.GREEN + Console.BOLD}+${Console.RESET}"
  private val FailureMark = s"${Console.RED + Console.BOLD}x${Console.RESET}"
  private val PendingLabel = "[PENDING]"
  private def commonRunTest(
      label: String,
      testDirectory: File,
      handlers: Map[Char, StatementHandler],
      runner: BatchScriptRunner,
      states: BatchScriptRunner.States,
      scriptedLogger: ScriptedLogger
  ): Option[String] = {
    val ScriptedLogger(logger, _, buffer) = scriptedLogger
    if (bufferLog) buffer.record()

    val (file, pending) = {
      val normal = new File(testDirectory, ScriptFilename)
      val pending = new File(testDirectory, PendingScriptFilename)
      if (pending.isFile) (pending, true) else (normal, false)
    }

    def testFailed(t: Throwable): Option[String] = {
      if (pending) {
        import sbt.internal.util.codec.JsonProtocol._
        // Use trace but in debug mode (default trace in `ManagedLogger` prints as error
        logger.logEvent(Level.Debug, TraceEvent("Debug", t, logger.channelName, logger.execId))
        buffer.clearBuffer()

        logger.error(s"Pending cause: '${t.getMessage}'")
        logger.error(s"$FailureMark $label $PendingLabel")
        None
      } else {
        logger.error(s"$FailureMark $label")
        logger.trace(t)
        Some(label)
      }
    }

    import scala.util.control.Exception.catching
    catching(classOf[PreciseScriptedError])
      .withApply(testFailed)
      .andFinally(buffer.stopBuffer())
      .apply {
        val parser = new TestScriptParser(handlers)
        val handlersAndStatements = parser.parse(file)
        runner.apply(handlersAndStatements, states)

        // Handle successful tests
        if (bufferLog) buffer.clearBuffer()
        if (pending) {
          logger.info(s"$SuccessMark $label $PendingLabel")
          logger.error(s" -> Pending test $label passed. Mark as passing to remove this failure.")
          Some(label)
        } else {
          logger.info(s"$SuccessMark $label")
          None
        }
      }
  }
}

object ScriptedTests {
  type TestRunner = () => Seq[Option[String]]
  val emptyCallback: File => Unit = _ => ()
}
