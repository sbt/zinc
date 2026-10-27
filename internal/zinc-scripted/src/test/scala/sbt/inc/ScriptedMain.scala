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

package sbt.inc

import sbt.internal.inc.*
import sbt.internal.scripted.ScriptedTest
import sbt.io.syntax.*
import sbt.io.{ AllPassFilter, IO, NameFilter }

object ScriptedMain:
  private val DisableBuffering = "--no-buffer"
  private val CompileToJar = "--to-jar"
  private val Parallelism = "--parallelism="
  private val Flags = Set(DisableBuffering, CompileToJar)

  def defaultParallelism: Int = Runtime.getRuntime.availableProcessors()

  def main(args: Array[String]): Unit =
    val compileToJar = args.contains(CompileToJar)
    val disableBuffering = args.contains(DisableBuffering)
    val parallelism = args.collectFirst {
      case arg if arg.startsWith(Parallelism) => arg.stripPrefix(Parallelism).toInt
    }
    val tests = args.filterNot(arg => Flags.contains(arg) || arg.startsWith(Parallelism))
    val baseDir = BuildInfo.sourceDirectory / "sbt-test"
    run(
      baseDir,
      buffer = !disableBuffering,
      compileToJar,
      parallelism.getOrElse(defaultParallelism),
      tests
    )

  def detectScriptedTests(scriptedBase: File): Map[String, Set[String]] =
    val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get().map { f =>
      val p = f.getParentFile
      (p.getParentFile.getName, p.getName)
    }

    pairs.groupBy(_._1).view.mapValues(_.map(_._2).toSet).toMap

  private def parseScripted(
      testsMapping: Map[String, Set[String]],
      scriptedBase: File,
      toParse: String
  ): Option[ScriptedTest] =
    toParse.split("/").map(_.trim) match
      case Array("") | Array("*")   => None
      case Array("*", target)       => Some(ScriptedTest("*", target))
      case Array(directory, target) =>
        val directoryPath = (scriptedBase / directory).getAbsoluteFile
        testsMapping.get(directory) match
          case Some(tests) if tests.isEmpty          => fail(s"No tests in ${directoryPath}")
          case Some(_) if target == "*"              => Some(ScriptedTest(directory, target))
          case Some(tests) if tests.contains(target) => Some(ScriptedTest(directory, target))
          case Some(_) => fail(s"Missing test directory ${directoryPath / target}")
          case None    => fail(s"Missing parent directory ${directoryPath}")
      case _ => fail("Expected only one '/' in the target scripted test(s).")

  // WARNING: called via reflection from project/Scripted.scala
  def run(
      baseDir: File,
      buffer: Boolean,
      compileToJar: Boolean,
      parallelism: Int,
      testSpecs: Array[String],
  ): Unit =
    val foundTests = detectScriptedTests(baseDir)
    val tests = testSpecs.toList.flatMap(arg => parseScripted(foundTests, baseDir, arg))

    if tests.isEmpty then
      println(s"About to run all scripted tests\n")
    else
      println(s"About to run tests: ${tests.mkString("\n * ", "\n * ", "\n")}")

    IO.withTemporaryDirectory { tempDir =>
      // Create a global temporary directory to store the bridge et al
      val handlers = new IncScriptedHandlers(tempDir.toPath, compileToJar)
      ScriptedRunnerImpl.run(baseDir.toPath, buffer, tests, handlers, parallelism)
    }

  private final class InvalidTestSpec(msg: String) extends RuntimeException(msg, null, false, false)

  private def fail(msg: String): Nothing = throw new InvalidTestSpec(msg)
end ScriptedMain
