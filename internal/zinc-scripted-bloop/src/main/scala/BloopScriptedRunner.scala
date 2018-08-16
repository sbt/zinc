package sbt.inc

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import sbt.io.IO
import sbt.internal.inc._
import sbt.internal.scripted.{CommentHandler, ScriptConfig, StatementHandler}
import sbt.internal.util.ManagedLogger
import xsbti.compile.CompilerBridgeProvider
import sbt.util.Logger
import xsbti.compile

final class BloopScriptedRunner {
  def run(bloopDir: File,
          resourceBaseDirectory: File,
          bufferLog: Boolean,
          tests: Array[String]): Unit = {
    IO.withTemporaryDirectory { tempDir =>
      // Create a global temporary directory to store the bridge et al
      val handlers = new BloopScriptedHandlers(bloopDir, tempDir)
      ScriptedRunnerImpl.run(resourceBaseDirectory, bufferLog, tests, handlers, 4)
    }
  }
}

final class BloopScriptedHandlers(bloopDir: File, tempDir: File)
    extends IncScriptedHandlers(tempDir) {
  val provider: CompilerBridgeProvider = {
    import sbt.io.syntax._
    val compilerBridge210 = ScriptedMain.configFromFile(bloopDir./("compilerBridge210.json"))
    val compilerBridge211 = ScriptedMain.configFromFile(bloopDir./("compilerBridge211.json"))
    val compilerBridge212 = ScriptedMain.configFromFile(bloopDir./("compilerBridge212.json"))
    // Bridges are already compiled, so let's bundle them with a provider
    val allBridges =
      List(compilerBridge210.project, compilerBridge211.project, compilerBridge212.project)
    new BloopBridgeProvider(compilerBridge212.project, allBridges, tempDir)
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
      new BloopIncHandler(config.testDirectory(), tempDir, provider, logger)
    }
  )
}

final class BloopIncHandler(
    directory: File,
    cacheDir: File,
    provider: CompilerBridgeProvider,
    logger: ManagedLogger
) extends IncHandler(directory, cacheDir, logger) {
  override def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider = provider
  import io.circe.{Decoder, parser}
  import io.circe.derivation._

  import scala.util.Try
  implicit val fileDecoder: Decoder[File] =
    Decoder.decodeString.emapTry(s => Try(java.nio.file.Paths.get(s).toFile))

  implicit val buildDecoder: Decoder[Build] = deriveDecoder
  implicit val projectDecoder: Decoder[Project] = deriveDecoder

  override def initBuild: Build = {
    val build = directory.toPath.resolve("build.json")
    if (Files.exists(build)) {
      val contents = new String(Files.readAllBytes(build), StandardCharsets.UTF_8)
      val parsed = parser.parse(contents).right.getOrElse(sys.error("error parsing"))
      buildDecoder.decodeJson(parsed) match {
          case Right(parsedConfig) => parsedConfig
          case Left(failure) => throw failure
      }
    } else Build(projects = Vector(Project(name = RootIdentifier).copy(in = Some(directory))))
  }
}
