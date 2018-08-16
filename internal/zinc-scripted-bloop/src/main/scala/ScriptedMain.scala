package sbt.inc

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import sbt.io.syntax._
import sbt.io.{ AllPassFilter, NameFilter }
import bloop.config.Config

object ScriptedMain {
  def main(args: Array[String]): Unit = {
    val workingDirectory = file(System.getProperty("user.dir"))
    val bloopDir = workingDirectory./(".bloop")
    if (!bloopDir.exists()) {
      System.err.println(s"Bloop config files missing in ${bloopDir.getAbsolutePath}")
      System.exit(1)
    } else {
      val scripted = configFromFile(bloopDir./("bloopScripted.json"))
      val zinc = configFromFile(bloopDir./("zinc.json"))

      val sourceDir = zinc.project.directory.resolve("src").resolve("sbt-test")
      val fullClasspath = scripted.project.classesDir :: scripted.project.classpath.toList
      val toParse = args.headOption.getOrElse("")
      val (isUserDefined, buffer) = args.lastOption match {
        case Some(last) =>
          if (last == "true") (true, true)
          else if (last == "false") (true, false)
          else (false, true)
        case None => (false, true)

      }

      val argsToParse = if (isUserDefined) args.init else args
      val tests = detectScriptedTests(sourceDir.toFile)
      val parsed = argsToParse.toList.flatMap(arg => parseScripted(tests, sourceDir.toFile, arg))
      runScripted(bloopDir, fullClasspath, sourceDir, parsed, buffer)
    }
  }

  def configFromFile(config: File): Config.File = {
    import io.circe.parser
    import bloop.config.ConfigEncoderDecoders._
    println(s"Loading project from '$config'")
    val contents = new String(Files.readAllBytes(config.toPath), StandardCharsets.UTF_8)
    val parsed =
      parser.parse(contents).right.getOrElse(sys.error(s"Parse error: ${config.getAbsolutePath}"))
    allDecoder.decodeJson(parsed) match {
      case Right(parsedConfig) => parsedConfig
      case Left(failure)       => throw failure
    }
  }

  private def detectScriptedTests(scriptedBase: File): Map[String, Set[String]] = {
    val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map {
      (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
    }

    pairs.groupBy(_._1).mapValues(_.map(_._2).toSet)
  }

  private def parseScripted(
      testsMapping: Map[String, Set[String]],
      scriptedBase: File,
      toParse: String
  ): Seq[String] = {
    if (toParse.isEmpty) List("*")
    else if (toParse == "*") List(toParse)
    else {
      toParse.split("/") match {
        case Array(directory, target) if directory == "*" => List(toParse)
        case Array(directory, target) =>
          val directoryPath = scriptedBase.toPath.resolve(directory).toAbsolutePath
          testsMapping.get(directory) match {
            case Some(tests) if tests.isEmpty          => fail(s"No tests in ${directoryPath}")
            case Some(tests) if target == "*"          => List(toParse)
            case Some(tests) if tests.contains(target) => List(toParse)
            case Some(tests)                           => fail(s"Missing test directory ${directoryPath.resolve(target)}")
            case None                                  => fail(s"Missing parent directory ${directoryPath}")
          }
        case _ => fail("Expected only one '/' in the target scripted test(s).")
      }
    }
  }

  type IncScriptedRunner = {
    def run(
        bloopDir: File,
        resourceBaseDirectory: File,
        bufferLog: Boolean,
        tests: Array[String]
    ): Unit
  }

  import sbt.internal.inc.classpath.ClasspathUtilities
  def runScripted(
      bloopDir: File,
      classpath: Seq[Path],
      source: Path,
      args: Seq[String],
      buffer: Boolean
  ): Unit = {
    println(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")
    // Force Log4J to not use a thread context classloader otherwise it throws a CCE
    sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"
    val loader = ClasspathUtilities.toLoader(classpath.map(_.toFile))
    val bridgeClass = Class.forName("sbt.inc.BloopScriptedRunner", true, loader)
    val bridge = bridgeClass.newInstance.asInstanceOf[IncScriptedRunner]
    try bridge.run(bloopDir, source.toFile, buffer, args.toArray)
    catch { case ite: java.lang.reflect.InvocationTargetException => throw ite.getCause }
  }

  private def fail(msg: String): Nothing = {
    println(msg)
    sys.exit(1)
  }
}
