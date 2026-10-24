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
import sbt.internal.util.ConsoleLogger
import xsbti.{ Position, Problem, Severity }
import HedgehogSupport.contains

object JavaErrorParserSpec extends Properties:
  override def tests: List[Test] = List(
    example("The JavaErrorParser should be able to parse Linux errors", parseSampleLinux),
    example("be able to parse windows file names", parseWindowsFile),
    example("be able to parse windows errors", parseSampleWindows),
    example("be able to parse javac non-problems", parseSampleNonProblem),
    example("be able to parse javac warnings", parseJavacWarning),
    example("be able to parse javac errors", parseSampleJavac),
    example("register the position of errors", parseErrorPosition),
    example("be able to parse multiple errors", parseMultipleErrors),
    example(
      "be able to parse multiple errors without carrets or indentation",
      parseMultipleErrors2
    ),
    property("parse the path, line, severity and pointer of any problem list", propProblems),
  )

  private def parse(in: String): List[Problem] =
    new JavaErrorParser().parseProblems(in, ConsoleLogger()).toList

  private def parseSampleLinux: Result =
    val problems = parse(sampleLinuxMessage)
    Result.all(
      List(
        problems.size ==== 1,
        sourcePath(problems.head) ==== Some("/home/me/projects/sample/src/main/Test.java"),
      )
    )

  private def parseSampleWindows: Result =
    val problems = parse(sampleWindowsMessage)
    Result.all(List(problems.size ==== 1, sourcePath(problems.head) ==== Some(windowsFile)))

  private def parseSampleNonProblem: Result =
    parse(sampleNonProblemMessage).size ==== 2

  private def parseWindowsFile: Result =
    val parser = new JavaErrorParser()
    parser.parse(parser.fileAndLineNo, sampleWindowsMessage) match
      case parser.Success((file, _), _) => file ==== windowsFile
      case parser.Error(msg, next)      =>
        Result.failure.log("Error to parse: " + msg + ", " + next.pos.longString)
      case parser.Failure(msg, next) =>
        Result.failure.log("Failed to parse: " + msg + ", " + next.pos.longString)

  private def parseJavacWarning: Result =
    val problems = parse(sampleJavacWarning)
    Result.all(
      List(
        problems.size ==== 1,
        problems.head.severity ==== Severity.Warn,
        problems.head.position.offset.isPresent ==== false,
      )
    )

  private def parseSampleJavac: Result =
    val problems = parse(sampleJavacMessage)
    Result.all(List(problems.size ==== 1, problems.head.message ==== sampleJavacMessage))

  private def sourcePath(p: Problem): Option[String] =
    if p.position.sourcePath.isPresent then Some(p.position.sourcePath.get) else None

  private def checkPosition(
      position: Position,
      path: String,
      line: Int,
      content: String,
      pointer: Int
  ): Result =
    Result.all(
      List(
        (if position.sourcePath.isPresent then Some(position.sourcePath.get) else None) ====
          Some(path),
        (if position.line.isPresent then Some(position.line.get.toInt) else None) ==== Some(line),
        contains(position.lineContent, content),
        (if position.pointer.isPresent then Some(position.pointer.get.toInt) else None) ====
          Some(pointer),
        (if position.pointerSpace.isPresent then Some(position.pointerSpace.get) else None) ====
          Some(" " * pointer),
      )
    )

  private def parseErrorPosition: Result =
    val problems = parse(sampleErrorPosition)
    Result.all(
      List(
        problems.size ==== 1,
        checkPosition(
          problems.head.position,
          "/home/me/projects/sample/src/main/java/A.java",
          6,
          "System.out.println(foobar);",
          23
        ),
      )
    )

  private def parseMultipleErrors: Result =
    val problems = parse(sampleMultipleErrors)
    val file = "/home/foo/sbt/internal/inc/javac/test1.java"
    val expected = List(
      (3, "public class Test {", 7),
      (1, "import java.rmi.RMISecurityException;", 15),
      (4, "public NotFound foo() { return 5; }", 11),
      (7, """throw new RMISecurityException("O NOES");""", 18),
      (7, """throw new RMISecurityException("O NOES");""", 14),
    )
    Result.all(
      (problems.size ==== 5) :: problems.zip(expected).map {
        case (p, (line, content, pointer)) =>
          checkPosition(p.position, file, line, content, pointer)
      }
    )

  private def parseMultipleErrors2: Result =
    val problems = parse(sampleLinuxMessage2)
    Result.all(
      List(
        problems.size ==== 3,
        sourcePath(problems.head) ==== Some("/home/me/projects/sample/src/main/Test.java"),
      )
    )

  private case class Sample(
      path: String,
      line: Int,
      severity: Severity,
      message: String,
      code: String,
      pointer: Int,
      details: List[String]
  ):
    def render: String =
      val kind = if severity == Severity.Warn then "warning: " else "error: "
      (s"$path:$line: $kind$message" :: code :: s"${" " * pointer}^" :: details.map("  " + _))
        .mkString("", "\n", "\n")

  private val genWords: Gen[String] =
    Gen.list(Gen.string(Gen.lower, Range.linear(1, 8)), Range.linear(1, 5)).map(_.mkString(" "))

  private val genSample: Gen[Sample] =
    for
      segments <- Gen.list(Gen.string(Gen.alphaNum, Range.linear(1, 8)), Range.linear(1, 4))
      line <- Gen.int(Range.linear(1, 10000))
      severity <- Gen.element1(Severity.Error, Severity.Warn)
      message <- genWords
      indent <- Gen.int(Range.linear(0, 8))
      code <- genWords
      pointer <- Gen.int(Range.linear(1, indent + code.length))
      details <- Gen.list(genWords, Range.linear(0, 2))
    yield Sample(
      segments.mkString("/", "/", ".java"),
      line,
      severity,
      message,
      " " * indent + code,
      pointer,
      details
    )

  def propProblems: Property =
    for samples <- Gen.list(genSample, Range.linear(1, 5)).forAll
    yield
      val input = samples.map(_.render).mkString
      val problems = parse(input)
      Result
        .all(
          (problems.size ==== samples.size) :: problems.zip(samples).map { (p, s) =>
            Result.all(
              List(
                p.severity ==== s.severity,
                contains(p.message, s.message),
                checkPosition(p.position, s.path, s.line, s.code, s.pointer),
              )
            )
          }
        )
        .log(input)

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
end JavaErrorParserSpec
