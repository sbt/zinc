/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc
package javac

import sbt.util.Logger

class JavaErrorParserSpec extends UnitSpec {

  "The JavaErrorParser" should "be able to parse linux errors" in parseSampleLinux()
  it should "be able to parse windows file names" in parseWindowsFile()
  it should "be able to parse windows errors" in parseSampleWindows()
  it should "be able to parse javac errors" in parseSampleJavac()
  it should "register the position of errors" in parseErrorPosition()
  it should "be able to parse multiple errors" in parseMultipleErrors()

  def parseSampleLinux() = {
    val parser = new JavaErrorParser()
    val logger = Logger.Null
    val problems = parser.parseProblems(sampleLinuxMessage, logger)

    problems should have size (1)
    problems(0).position.sourcePath.get shouldBe ("/home/me/projects/sample/src/main/Test.java")

  }

  def parseSampleWindows() = {
    val parser = new JavaErrorParser()
    val logger = Logger.Null
    val problems = parser.parseProblems(sampleWindowsMessage, logger)

    problems should have size (1)
    problems(0).position.sourcePath.get shouldBe (windowsFile)

  }

  def parseWindowsFile() = {
    val parser = new JavaErrorParser()
    parser.parse(parser.fileAndLineNo, sampleWindowsMessage) match {
      case parser.Success((file, line), rest) => file shouldBe (windowsFile)
      case parser.Error(msg, next) =>
        assert(false, s"Error to parse: $msg, ${next.pos.longString}")
      case parser.Failure(msg, next) =>
        assert(false, s"Failed to parse: $msg, ${next.pos.longString}")
    }
  }

  def parseSampleJavac() = {
    val parser = new JavaErrorParser()
    val logger = Logger.Null
    val problems = parser.parseProblems(sampleJavacMessage, logger)
    problems should have size (1)
    problems(0).message shouldBe (sampleJavacMessage)
  }

  def parseErrorPosition() = {
    val parser = new JavaErrorParser()
    val logger = Logger.Null
    val problems = parser.parseProblems(sampleErrorPosition, logger)
    problems should have size (1)
    problems(0).position.offset.isPresent shouldBe true
    problems(0).position.offset.get shouldBe 23
  }

  def parseMultipleErrors() = {
    val parser = new JavaErrorParser()
    val logger = Logger.Null
    val problems = parser.parseProblems(sampleMultipleErrors, logger)
    problems should have size (5)
  }

  def sampleLinuxMessage =
    """
      |/home/me/projects/sample/src/main/Test.java:4: cannot find symbol
      |symbol  : method baz()
      |location: class Foo
      |return baz();
    """.stripMargin

  def sampleWindowsMessage =
    s"""
      |$windowsFile:4: cannot find symbol
      |symbol  : method baz()
      |location: class Foo
      |return baz();
    """.stripMargin

  def windowsFile = """C:\Projects\sample\src\main\java\Test.java"""
  def windowsFileAndLine = s"""$windowsFile:4"""

  def sampleJavacMessage = "javac: invalid flag: -foobar"

  def sampleErrorPosition =
    """
      |A.java:6: cannot find symbol
      |symbol  : variable foobar
      |location: class A
      |    System.out.println(foobar);
      |                       ^
    """.stripMargin

  def sampleMultipleErrors =
    """/home/foo/sbt/internal/inc/javac/test1.java:3: error: class Test is public, should be declared in a file named Test.java
       |public class Test {
       |       ^
       |/home/foo/sbt/internal/inc/javac/test1.java:1: warning: [deprecation] RMISecurityException in java.rmi has been deprecated
       |import java.rmi.RMISecurityException;
       |               ^
       |/home/foo/sbt/internal/inc/javac/test1.java:4: error: cannot find symbol
       |    public NotFound foo() { return 5; }
       |           ^
       |  symbol:   class NotFound
       |  location: class Test
       |/home/foo/sbt/internal/inc/javac/test1.java:7: warning: [deprecation] RMISecurityException in java.rmi has been deprecated
       |        throw new RMISecurityException("O NOES");
       |                  ^
       |/home/foo/sbt/internal/inc/javac/test1.java:7: warning: [deprecation] RMISecurityException(String) in RMISecurityException has been deprecated
       |        throw new RMISecurityException("O NOES");
       |              ^
       |""".stripMargin
}
