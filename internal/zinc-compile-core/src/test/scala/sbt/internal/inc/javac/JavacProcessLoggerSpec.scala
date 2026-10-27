/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc
package javac

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.io.File
import sbt.util.Level
import HedgehogSupport.contains

object JavacProcessLoggerSpec extends Properties:
  override def tests: List[Test] = List(
    example("The javac process logger should parse regular semantic errors", logSemanticErrors),
    example("parse semantic errors passed in one by one", logSeparateSemanticErrors),
    example("log errors that could not be parsed", logUnparsableErrors),
    example("ignore javadoc info logs", ignoreJavadocInfoLogs),
    property("report every error however it is split across err calls", propSplitErrors),
    property("warn about exactly the non-zero exit codes", propExitCode),
  )

  private val cannotFindSymbol =
    """/home/someone/Test.java:4: cannot find symbol
      |symbol  : method baz()
      |location: class Foo
      |return baz();
      |       ^
      |""".stripMargin

  private val deprecation =
    """/home/someone/Test.java:8: warning: [deprecation] RMISecurityException(java.lang.String) in java.rmi.RMISecurityException has been deprecated
      |throw new java.rmi.RMISecurityException("O NOES");
      |^
      |""".stripMargin

  private def run(
      toolname: String,
      exitCode: Int
  )(errs: String*): (CollectingReporter, CollectingLogger) =
    val reporter = new CollectingReporter()
    val errorLogger = new CollectingLogger()
    val javacLogger = new JavacLogger(errorLogger, reporter, cwd = new File("."))
    errs.foreach(javacLogger.err(_))
    javacLogger.flush(toolname, exitCode)
    (reporter, errorLogger)

  private def logSemanticErrors: Result =
    val (reporter, errorLogger) = run("javac", 0)(Seq(cannotFindSymbol, deprecation).mkString("\n"))
    Result.all(List(errorLogger.messages ==== Map.empty, reporter.problems.length ==== 2))

  private def logSeparateSemanticErrors: Result =
    val (reporter, errorLogger) = run("javac", 0)(cannotFindSymbol, deprecation)
    Result.all(List(errorLogger.messages ==== Map.empty, reporter.problems.length ==== 2))

  private def logUnparsableErrors: Result =
    val (_, errorLogger) = run("javadoc", -1)("javadoc: error - invalid flag: -target")
    val warnings = errorLogger.messages(Level.Warn).toList
    Result.all(
      List(
        warnings.length ==== 2,
        contains(warnings(0), "javadoc: error - invalid flag: -target"),
        contains(warnings(1), "javadoc exited with exit code -1"),
      )
    )

  private def ignoreJavadocInfoLogs: Result =
    val javadocLogs =
      """|Loading source file /foo/bar/MyClass.java...
         |Constructing Javadoc information...
         |Building index for all the packages and classes...
         |Standard Doclet version 21.0.4+7-LTS
         |Building tree for all the packages and classes...
         |Generating /foo/bar/MyClass.html...
         |""".stripMargin
    val (reporter, errorLogger) = run("javadoc", 0)(javadocLogs)
    Result.all(List(reporter.problems.length ==== 0, errorLogger.messages ==== Map.empty))

  def propSplitErrors: Property =
    for
      errors <- Gen.list(Gen.element1(cannotFindSymbol, deprecation), Range.linear(0, 8)).forAll
      calls <- Gen.list(Gen.boolean, Range.singleton(errors.size)).forAll
    yield
      val chunks = errors.zip(calls).foldLeft(List.empty[String]) {
        case (last :: rest, (error, false)) => (last + "\n" + error) :: rest
        case (acc, (error, _))              => error :: acc
      }
      val (reporter, errorLogger) = run("javac", 0)(chunks.reverse*)
      Result.all(
        List(errorLogger.messages ==== Map.empty, reporter.problems.length ==== errors.size)
      )

  def propExitCode: Property =
    for exitCode <-
        Gen.frequency1(1 -> Gen.constant(0), 3 -> Gen.int(Range.linearFrom(0, -255, 255))).forAll
    yield
      val (_, errorLogger) = run("javac", exitCode)()
      val warnings = errorLogger.messages(Level.Warn).toList
      if exitCode == 0 then warnings ==== Nil
      else warnings ==== List(s"javac exited with exit code $exitCode")
end JavacProcessLoggerSpec
