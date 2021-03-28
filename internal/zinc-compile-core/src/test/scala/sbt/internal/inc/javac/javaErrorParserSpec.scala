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

package sbt
package internal
package inc
package javac

import sbt.internal.util.ConsoleLogger
import org.scalatest.diagrams.Diagrams

class JavaErrorParserSpec extends UnitSpec with Diagrams {

  "The JavaErrorParser" should "be able to parse Linux errors" in parseSampleLinux()
  it should "be able to parse windows file names" in parseWindowsFile()
  it should "be able to parse windows errors" in parseSampleWindows()
  it should "be able to parse javac errors" in parseSampleJavac()
  it should "register the position of errors" in parseErrorPosition()
  it should "be able to parse multiple errors" in parseMultipleErrors()
  it should "be able to parse multiple errors without carrets or indentation" in parseMultipleErrors2()

  def parseSampleLinux() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleLinuxMessage, logger)

    assert(problems.size == 1)
    assert(problems(0).position.sourcePath.get == ("/home/me/projects/sample/src/main/Test.java"))
  }

  def parseSampleWindows() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleWindowsMessage, logger)

    assert(problems.size == 1)
    problems(0).position.sourcePath.get shouldBe (windowsFile)

  }

  def parseWindowsFile() = {
    val parser = new JavaErrorParser()
    parser.parse(parser.fileAndLineNo, sampleWindowsMessage) match {
      case parser.Success((file, _), _) => file shouldBe (windowsFile)
      case parser.Error(msg, next) =>
        assert(false, "Error to parse: " + msg + ", " + next.pos.longString)
      case parser.Failure(msg, next) =>
        assert(false, "Failed to parse: " + msg + ", " + next.pos.longString)
    }
  }

  def parseSampleJavac() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleJavacMessage, logger)
    assert(problems.size == 1)
    problems(0).message shouldBe (sampleJavacMessage)
  }

  def parseErrorPosition() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleErrorPosition, logger)
    assert(problems.size == 1)
    problems(0).position.offset.isPresent shouldBe true
    problems(0).position.offset.get shouldBe 23
  }

  def parseMultipleErrors() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleMultipleErrors, logger)
    assert(problems.size == 5)
  }

  def parseMultipleErrors2() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleLinuxMessage2, logger)

    assert(problems.size == 3)
    assert(problems(0).position.sourcePath.get == ("/home/me/projects/sample/src/main/Test.java"))
  }

  def sampleLinuxMessage =
    """
      |/home/me/projects/sample/src/main/Test.java:18: error: cannot find symbol
      |          baz();
      |          ^
      |  symbol:   method baz()
      |  location: class AbstractActorRef
      |1 error.
      |""".stripMargin

  def sampleLinuxMessage2 =
    """
      |/home/me/projects/sample/src/main/Test.java:100:1: cannot find symbol
      |symbol: method isBar()
      |location: variable x of type com.example.List
      |if (x.isBar()) {
      |/home/me/projects/sample/src/main/Test.java:200:1: cannot find symbol
      |symbol: method isBar()
      |location: variable x of type com.example.List
      |} else if (x.isBar()) {
      |/home/me/projects/sample/src/main/Test.java:300:1: cannot find symbol
      |symbol: method isBar()
      |location: variable x of type com.example.List
      |foo.baz(runtime, x.isBar());
      |""".stripMargin

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
       |5 errors.
       |""".stripMargin
}
