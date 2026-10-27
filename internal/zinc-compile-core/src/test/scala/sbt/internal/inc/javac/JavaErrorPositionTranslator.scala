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

object JavaErrorPositionTranslator extends Properties:
  private val helloSource =
    "\npublic class Hello {\n    UnknownClass someVar;\n    public static void main(String[] args) {\n        System.out.println(\"Hello World\");\n    }\n}"
  private val finalizeSource =
    "\n\n\tprotected void finalize() throws Throwable\n\t{\n\t\ttry\n\t\t{\n\t\t\tdispose();\n\t\t}\n\t\tfinally\n\t\t{\n\t\t\tsuper.finalize();\n\t\t}\n\t}\n\t"

  override def tests: List[Test] = List(
    translate("1", testHighlight("hello", 0, 5)(1, 0, 1, 5, "hello")),
    translate(
      "2",
      // on Java an "; expected" error message has no text to return
      // should the first char of the next line be specified?
      testHighlight("foo", 3, 3)(1, 3, 1, 3, "")
    ),
    translate("3", testHighlight("\nhello", 1, 1)(2, 0, 2, 0, "")),
    translate("4", testHighlight("\nhello", 0, 0)(1, 0, 1, 0, "")),
    translate("5", testHighlight("\nhello", 1, 5)(2, 0, 2, 4, "hell")),
    translate("6", testHighlight("hello", 0, 1)(1, 0, 1, 1, "h")),
    translate("7", testHighlight("h\n", 0, 1)(1, 0, 1, 1, "h")),
    translate("8", testHighlight(helloSource, 26, 38)(3, 4, 3, 16, "UnknownClass")),
    translate(
      "9",
      testHighlight(helloSource, 0, 38)(1, 0, 3, 16, "\npublic class Hello {\n    UnknownClass")
    ),
    translate("10", testHighlight("\n\n\n", 0, 0)(1, 0, 1, 0, "")),
    translate("11", testHighlight("\n\n\n", 1, 1)(2, 0, 2, 0, "")),
    translate("12", testHighlight("", 0, 0)(1, 0, 1, 0, "")),
    translate("13", testHighlight("foo", 0, 0)(1, 0, 1, 0, "")),
    translate(
      "14",
      testHighlight(finalizeSource, 3, 118)(
        3,
        1,
        13,
        2,
        "protected void finalize() throws Throwable\n\t{\n\t\ttry\n\t\t{\n\t\t\tdispose();\n\t\t}\n\t\tfinally\n\t\t{\n\t\t\tsuper.finalize();\n\t\t}\n\t}"
      )
    ),
    property(
      "The JavaErrorPositionTranslator should translate any range to the lines and columns of its ends",
      propRange
    ),
  )

  private def translate(n: String, result: => Result): Test =
    example(s"The JavaErrorPositionTranslator should translate file positions $n", result)

  private def testSingleHighlight(code: String, startPos: Long, endPos: Long)(
      startLine: Int,
      startCol: Int,
      endLine: Int,
      endCol: Int,
      text: String
  ): Result =
    val (line1, col1, line2, col2, content) =
      DiagnosticsReporter.contentAndRanges(code, startPos, endPos)
    Result
      .all(
        List(
          content ==== text,
          line1.toInt ==== startLine,
          col1.toInt ==== startCol,
          line2.toInt ==== endLine,
          col2.toInt ==== endCol,
        )
      )
      .log(s"code: ${code.replace("\r", "\\r").replace("\n", "\\n")} [$startPos, $endPos)")

  private def testHighlight(code: String, startPos: Long, endPos: Long)(
      startLine: Int,
      startCol: Int,
      endLine: Int,
      endCol: Int,
      text: String
  ): Result =
    // test with /n /r and /r/n variations
    val codeN = code.replace('\r', '\n')
    val textN = text.replace('\r', '\n')
    def newlinesBefore(pos: Long) = codeN.substring(0, pos.toInt).count(_ == '\n')
    Result.all(
      List(
        testSingleHighlight(codeN, startPos, endPos)(startLine, startCol, endLine, endCol, textN),
        testSingleHighlight(codeN.replace('\n', '\r'), startPos, endPos)(
          startLine,
          startCol,
          endLine,
          endCol,
          textN.replace('\n', '\r')
        ),
        testSingleHighlight(
          codeN.replace("\n", "\r\n"),
          startPos + newlinesBefore(startPos),
          endPos + newlinesBefore(endPos)
        )(startLine, startCol, endLine, endCol, textN.replace("\n", "\r\n")),
      )
    )
  end testHighlight

  private def lineAndColumn(code: String, pos: Int): (Int, Int) =
    val before = code.substring(0, pos)
    (before.count(_ == '\n') + 1, pos - (before.lastIndexOf('\n') + 1))

  def propRange: Property =
    for
      code <- Gen
        .string(
          Gen.frequency1(4 -> Gen.alphaNum, 1 -> Gen.element1(' ', '\t', '\n')),
          Range.linear(0, 80)
        )
        .forAll
      start <- Gen.int(Range.linear(0, code.length)).forAll
      end <- Gen.int(Range.linear(start, code.length)).forAll
    yield
      val (startLine, startCol) = lineAndColumn(code, start)
      val (endLine, endCol) = lineAndColumn(code, end)
      testHighlight(
        code,
        start,
        end
      )(startLine, startCol, endLine, endCol, code.substring(start, end))
end JavaErrorPositionTranslator
