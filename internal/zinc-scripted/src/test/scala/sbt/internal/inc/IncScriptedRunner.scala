package sbt.internal.inc

import java.io.File

import sbt.internal.scripted.{ HandlersProvider, ListTests, ScriptedTest }
import sbt.io.IO
import sbt.util.Logger

import scala.collection.parallel.ParSeq

class IncScriptedRunner {
  def run(
      resourceBaseDirectory: File,
      bufferLog: Boolean,
      compileToJar: Boolean,
      tests: Array[String]
  ): Unit = {
    IO.withTemporaryDirectory { tempDir =>
      // Create a global temporary directory to store the bridge et al
      val handlers = new IncScriptedHandlers(tempDir, compileToJar)
      ScriptedRunnerImpl.run(resourceBaseDirectory, bufferLog, tests, handlers, 4)
    }
  }
}

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
    val logsDir = newScriptedLogsDir
    val runner = new ScriptedTests(resourceBaseDirectory, bufferLog, handlersProvider, logsDir)
    val scriptedTests = get(tests, resourceBaseDirectory, globalLogger)
    val scriptedRunners = runner.batchScriptedRunner(scriptedTests, instances)
    val parallelRunners = scriptedRunners.toParArray
    // Using this deprecated value for 2.11 support
    val pool = new scala.concurrent.forkjoin.ForkJoinPool(instances)
    parallelRunners.tasksupport = new scala.collection.parallel.ForkJoinTaskSupport(pool)
    runAllInParallel(parallelRunners)
    globalLogger.info(s"Log files can be found at ${logsDir.getAbsolutePath}")
  }

  private val nl = IO.Newline
  private val nlt = nl + "\t"
  class ScriptedFailure(tests: Seq[String]) extends RuntimeException(tests.mkString(nlt, nlt, nl)) {
    // We are not interested in the stack trace here, only the failing tests
    override def fillInStackTrace = this
  }

  private def reportErrors(errors: Seq[String]): Unit =
    if (errors.nonEmpty) throw new ScriptedFailure(errors) else ()

  def runAllInParallel(tests: ParSeq[TestRunner]): Unit = {
    reportErrors(tests.flatMap(test => test.apply().flatten.toSeq).toList)
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
