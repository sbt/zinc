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

import sbt.internal.util.ConsoleLogger
import org.scalatest.diagrams.Diagrams
import xsbti.Severity

class JavaErrorParserSpec extends UnitSpec with Diagrams {

  "The JavaErrorParser" should "be able to parse Linux errors" in parseSampleLinux()
  it should "be able to parse windows file names" in parseWindowsFile()
  it should "be able to parse windows errors" in parseSampleWindows()
  it should "be able to parse javac non-problems" in parseSampleNonProblem()
  it should "be able to parse javac warnings" in parseJavacWarning()
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

  def parseSampleNonProblem() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleNonProblemMessage, logger)

    assert(problems.size == 2)
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

  def parseJavacWarning() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleJavacWarning, logger)
    assert(problems.size == 1)
    problems(0).severity shouldBe Severity.Warn
    problems(0).position.offset.isPresent shouldBe false
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
    val position = problems.head.position

    position.sourcePath.isPresent shouldBe true
    position.sourcePath.get shouldBe "/home/me/projects/sample/src/main/java/A.java"
    position.line.isPresent shouldBe true
    position.line.get shouldBe 6
    position.lineContent should include("System.out.println(foobar);")
    position.pointer.isPresent shouldBe true
    position.pointer.get shouldBe 23
    position.pointerSpace.isPresent shouldBe true
    position.pointerSpace.get shouldBe (" " * 23)
  }

  def parseMultipleErrors() = {
    val parser = new JavaErrorParser()
    val logger = ConsoleLogger()
    val problems = parser.parseProblems(sampleMultipleErrors, logger)
    assert(problems.size == 5)

    val position1 = problems(0).position
    position1.sourcePath.isPresent shouldBe true
    position1.sourcePath.get shouldBe "/home/foo/sbt/internal/inc/javac/test1.java"
    position1.line.isPresent shouldBe true
    position1.line.get shouldBe 3
    position1.lineContent should include("public class Test {")
    position1.pointer.isPresent shouldBe true
    position1.pointer.get shouldBe 7
    position1.pointerSpace.isPresent shouldBe true
    position1.pointerSpace.get shouldBe (" " * 7)

    val position2 = problems(1).position
    position2.sourcePath.isPresent shouldBe true
    position2.sourcePath.get shouldBe "/home/foo/sbt/internal/inc/javac/test1.java"
    position2.line.isPresent shouldBe true
    position2.line.get shouldBe 1
    position2.lineContent should include("import java.rmi.RMISecurityException;")
    position2.pointer.isPresent shouldBe true
    position2.pointer.get shouldBe 15
    position2.pointerSpace.isPresent shouldBe true
    position2.pointerSpace.get shouldBe (" " * 15)

    val position3 = problems(2).position
    position3.sourcePath.isPresent shouldBe true
    position3.sourcePath.get shouldBe "/home/foo/sbt/internal/inc/javac/test1.java"
    position3.line.isPresent shouldBe true
    position3.line.get shouldBe 4
    position3.lineContent should include("public NotFound foo() { return 5; }")
    position3.pointer.isPresent shouldBe true
    position3.pointer.get shouldBe 11
    position3.pointerSpace.isPresent shouldBe true
    position3.pointerSpace.get shouldBe (" " * 11)

    val position4 = problems(3).position
    position4.sourcePath.isPresent shouldBe true
    position4.sourcePath.get shouldBe "/home/foo/sbt/internal/inc/javac/test1.java"
    position4.line.isPresent shouldBe true
    position4.line.get shouldBe 7
    position4.lineContent should include("""throw new RMISecurityException("O NOES");""")
    position4.pointer.isPresent shouldBe true
    position4.pointer.get shouldBe 18
    position4.pointerSpace.isPresent shouldBe true
    position4.pointerSpace.get shouldBe (" " * 18)

    val position5 = problems(4).position
    position5.sourcePath.isPresent shouldBe true
    position5.sourcePath.get shouldBe "/home/foo/sbt/internal/inc/javac/test1.java"
    position5.line.isPresent shouldBe true
    position5.line.get shouldBe 7
    position5.lineContent should include("""throw new RMISecurityException("O NOES");""")
    position5.pointer.isPresent shouldBe true
    position5.pointer.get shouldBe 14
    position5.pointerSpace.isPresent shouldBe true
    position5.pointerSpace.get shouldBe (" " * 14)
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

  def sampleNonProblemMessage =
    raw"""
       |Loading source file D:\Repos\zinc\internal\zinc-compile-core\target\jvm-2.13\test-classes\sbt\internal\inc\javac\test1.java...
       |Constructing Javadoc information...
       |D:\Repos\zinc\internal\zinc-compile-core\target\jvm-2.13\test-classes\sbt\internal\inc\javac\test1.java:14: error: class Test is public, should be declared in a file named Test.java
       |public class Test {
       |       ^
       |D:\Repos\zinc\internal\zinc-compile-core\target\jvm-2.13\test-classes\sbt\internal\inc\javac\test1.java:15: error: cannot find symbol
       |    public NotFound foo() { return 5; }
       |           ^
       |  symbol:   class NotFound
       |  location: class Test
       |2 errors
    """.stripMargin.trim

  def sampleJavacWarning =
    "warning: [options] system modules path not set in conjunction with -source 17"

  def windowsFile = """C:\Projects\sample\src\main\java\Test.java"""
  def windowsFileAndLine = s"""$windowsFile:4"""

  def sampleJavacMessage = "javac: invalid flag: -foobar"

  def sampleErrorPosition =
    """
      |/home/me/projects/sample/src/main/java/A.java:6: cannot find symbol
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
