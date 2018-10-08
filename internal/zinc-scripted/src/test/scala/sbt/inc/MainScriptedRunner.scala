package sbt.inc

import java.io.File

import sbt.io.IO
import sbt.internal.inc._
import sbt.internal.scripted.{ CommentHandler, ScriptConfig, StatementHandler }
import sbt.internal.util.ManagedLogger
import xsbti.compile.CompilerBridgeProvider
import sbt.util.Logger

final class MainScriptedRunner {
  def run(
      resourceBaseDirectory: File,
      bufferLog: Boolean,
      compileToJar: Boolean,
      tests: Array[String]
  ): Unit = {
    IO.withTemporaryDirectory { tempDir =>
      // Create a global temporary directory to store the bridge et al
      val handlers = new MainScriptedHandlers(tempDir, compileToJar)
      ScriptedRunnerImpl.run(resourceBaseDirectory, bufferLog, tests, handlers, 4)
    }
  }
}

final class MainScriptedHandlers(tempDir: File, compileToJar: Boolean)
    extends IncScriptedHandlers(tempDir, compileToJar) {
  // Create a provider that uses the bridges from the classes directory of the projects
  val provider: CompilerBridgeProvider = {
    val compilerBridge210 = ScalaBridge(
      BuildInfo.scalaVersion210,
      BuildInfo.scalaJars210,
      BuildInfo.classDirectory210
    )
    val compilerBridge211 = ScalaBridge(
      BuildInfo.scalaVersion211,
      BuildInfo.scalaJars211,
      BuildInfo.classDirectory211
    )
    val compilerBridge212 = ScalaBridge(
      BuildInfo.scalaVersion212,
      BuildInfo.scalaJars212,
      BuildInfo.classDirectory212
    )
    val compilerBridge213 = ScalaBridge(
      BuildInfo.scalaVersion213,
      BuildInfo.scalaJars213,
      BuildInfo.classDirectory213
    )

    val all = List(compilerBridge210, compilerBridge211, compilerBridge212, compilerBridge213)
    new ScriptedBridgeProvider(all, tempDir)
  }

  override def getHandlers(config: ScriptConfig): Map[Char, StatementHandler] = Map(
    '$' -> new SleepingHandler(new ZincFileCommands(config.testDirectory()), 500),
    '#' -> CommentHandler,
    '>' -> {
      val logger: ManagedLogger =
        config.logger() match {
          case x: ManagedLogger => x
          case _                => sys.error("Expected ManagedLogger")
        }
      new BloopIncHandler(config.testDirectory(), tempDir, provider, logger, compileToJar)
    }
  )
}

final class BloopIncHandler(
    directory: File,
    cacheDir: File,
    provider: CompilerBridgeProvider,
    logger: ManagedLogger,
    compileToJar: Boolean
) extends IncHandler(directory, cacheDir, logger, compileToJar) {
  override def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider = provider
}
