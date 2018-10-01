package sbt.inc

import java.io.File

import sbt.internal.inc.BuildInfo
import sbt.io.syntax._
import sbt.io.{ AllPassFilter, NameFilter }

import scala.language.reflectiveCalls

object ScriptedMain {
  private val DisableBuffering = "--no-buffer"
  private val CompileToJar = "--to-jar"
  private val Flags = Set(DisableBuffering, CompileToJar)

  def main(args: Array[String]): Unit = {
    val compileToJar = args.contains(CompileToJar)
    val disableBuffering = args.contains(DisableBuffering)
    val argsToParse = args.filterNot(Flags.contains)

    val sourceDir = BuildInfo.sourceDirectory.toPath.resolve("sbt-test").toFile
    val tests = detectScriptedTests(sourceDir)
    val parsed = argsToParse.toList.flatMap(arg => parseScripted(tests, sourceDir, arg))
    runScripted(sourceDir, parsed, buffer = !disableBuffering, compileToJar)
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
    if (toParse.isEmpty) List()
    else if (toParse == "*") List()
    else {
      toParse.split("/") match {
        case Array(directory, _) if directory == "*" => List(toParse)
        case Array(directory, target) =>
          val directoryPath = scriptedBase.toPath.resolve(directory).toAbsolutePath
          testsMapping.get(directory) match {
            case Some(tests) if tests.isEmpty          => fail(s"No tests in ${directoryPath}")
            case Some(_) if target == "*"              => List(toParse)
            case Some(tests) if tests.contains(target) => List(toParse)
            case Some(_)                               => fail(s"Missing test directory ${directoryPath.resolve(target)}")
            case None                                  => fail(s"Missing parent directory ${directoryPath}")
          }
        case _ => fail("Expected only one '/' in the target scripted test(s).")
      }
    }
  }

  type IncScriptedRunner = {
    def run(
        resourceBaseDirectory: File,
        bufferLog: Boolean,
        compileToJar: Boolean,
        tests: Array[String]
    ): Unit
  }

  import sbt.internal.inc.classpath.ClasspathUtilities
  def runScripted(
      source: File,
      args: Seq[String],
      buffer: Boolean,
      compileToJar: Boolean
  ): Unit = {
    println(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")
    // Force Log4J to not use a thread context classloader otherwise it throws a CCE
    sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"
    val classpath = BuildInfo.test_classDirectory +: BuildInfo.classpath
    val loader = ClasspathUtilities.toLoader(classpath)
    val bridgeClass = Class.forName("sbt.inc.MainScriptedRunner", true, loader)
    val bridge = bridgeClass.newInstance.asInstanceOf[IncScriptedRunner]
    try bridge.run(source, buffer, compileToJar, args.toArray)
    catch { case ite: java.lang.reflect.InvocationTargetException => throw ite.getCause }
  }

  private def fail(msg: String): Nothing = {
    println(msg)
    sys.exit(1)
  }
}
