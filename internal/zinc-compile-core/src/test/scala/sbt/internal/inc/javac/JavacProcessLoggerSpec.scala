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

import java.io.File

import sbt.util.Level

class JavacProcessLoggerSpec extends UnitSpec {
  "The javac process logger" should "parse regular semantic errors" in logSemanticErrors()
  it should "parse semantic errors passed in one by one" in logSeparateSemanticErrors()
  it should "log errors that could not be parsed" in logUnparsableErrors()
  it should "ignore javadoc info logs" in ignoreJavadocInfoLogs()

  def logSemanticErrors(): Unit = {
    val reporter = new CollectingReporter()
    val errorLogger = new CollectingLogger()
    val javacLogger = new JavacLogger(errorLogger, reporter, cwd = new File("."))
    javacLogger.err(
      Seq(
        """/home/someone/Test.java:4: cannot find symbol
                          |symbol  : method baz()
                          |location: class Foo
                          |return baz();
                          |       ^
                          |""",
        """/home/someone/Test.java:8: warning: [deprecation] RMISecurityException(java.lang.String) in java.rmi.RMISecurityException has been deprecated
                                  |throw new java.rmi.RMISecurityException("O NOES");
                                  |^
                                  |"""
      ).mkString("\n")
    )

    javacLogger.flush("javac", 0)

    errorLogger.messages shouldBe Map.empty
    reporter.problems.length shouldBe 2
    ()
  }

  def logSeparateSemanticErrors(): Unit = {
    val reporter = new CollectingReporter()
    val errorLogger = new CollectingLogger()
    val javacLogger = new JavacLogger(errorLogger, reporter, cwd = new File("."))
    javacLogger.err("""/home/someone/Test.java:4: cannot find symbol
                          |symbol  : method baz()
                          |location: class Foo
                          |return baz();
                          |       ^
                          |""")
    javacLogger.err(
      """/home/someone/Test.java:8: warning: [deprecation] RMISecurityException(java.lang.String) in java.rmi.RMISecurityException has been deprecated
                                  |throw new java.rmi.RMISecurityException("O NOES");
                                  |^
                                  |"""
    )

    javacLogger.flush("javac", 0)

    errorLogger.messages shouldBe Map.empty
    reporter.problems.length shouldBe 2
    ()
  }

  def logUnparsableErrors(): Unit = {
    val reporter = new CollectingReporter()
    val errorLogger = new CollectingLogger()
    val javacLogger = new JavacLogger(errorLogger, reporter, cwd = new File("."))
    javacLogger.err("javadoc: error - invalid flag: -target")

    javacLogger.flush("javadoc", -1)

    errorLogger.messages(Level.Warn).length shouldBe 2
    errorLogger
      .messages(Level.Warn)(0)
      .contains("javadoc: error - invalid flag: -target") shouldBe true
    errorLogger.messages(Level.Warn)(1).contains("javadoc exited with exit code -1") shouldBe true
    ()
  }

  def ignoreJavadocInfoLogs(): Unit = {
    val reporter = new CollectingReporter()
    val errorLogger = new CollectingLogger()
    val javacLogger = new JavacLogger(errorLogger, reporter, cwd = new File("."))

    val javadocLogs =
      """|Loading source file /foo/bar/MyClass.java...
         |Constructing Javadoc information...
         |Building index for all the packages and classes...
         |Standard Doclet version 21.0.4+7-LTS
         |Building tree for all the packages and classes...
         |Generating /foo/bar/MyClass.html...
         |""".stripMargin

    javacLogger.err(javadocLogs)
    javacLogger.flush("javadoc", 0)

    reporter.problems shouldBe empty
    errorLogger.messages shouldBe empty
    ()
  }
}
