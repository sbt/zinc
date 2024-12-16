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

class JavaErrorPositionTranslator extends UnitSpec {

  "The JavaErrorPositionTranslator" should "be able to translate file positions to line+column positions 1" in translate1()
  it should "be able to translate file positions to line+column positions 2" in translate2()
  it should "be able to translate file positions to line+column positions 3" in translate3()
  it should "be able to translate file positions to line+column positions 4" in translate4()
  it should "be able to translate file positions to line+column positions 5" in translate5()
  it should "be able to translate file positions to line+column positions 6" in translate6()
  it should "be able to translate file positions to line+column positions 7" in translate7()
  it should "be able to translate file positions to line+column positions 8" in translate8()
  it should "be able to translate file positions to line+column positions 9" in translate9()
  it should "be able to translate file positions to line+column positions 10" in translate10()
  it should "be able to translate file positions to line+column positions 11" in translate11()
  it should "be able to translate file positions to line+column positions 12" in translate12()
  it should "be able to translate file positions to line+column positions 13" in translate13()
  it should "be able to translate file positions to line+column positions 14" in translate14()

  private def testSingleHighlight(
      code: String,
      startPos: Long,
      endPos: Long
  )(startLine: Int, startCol: Int, endLine: Int, endCol: Int, text: String): Unit = {
    val position = DiagnosticsReporter.contentAndRanges(code, startPos, endPos)
    position._5 shouldBe text
    position._1 shouldBe startLine
    position._2 shouldBe startCol
    position._3 shouldBe endLine
    position._4 shouldBe endCol
    ()
  }

  private def testHighlight(
      code: String,
      startPos: Long,
      endPos: Long
  )(startLine: Int, endLine: Int, startCol: Int, endCol: Int, text: String): Unit = {
    // test with /n /r and /r/n variations
    val codeN = code.replace('\r', '\n')
    val textN = text.replace('\r', '\n')
    testSingleHighlight(codeN, startPos, endPos)(startLine, endLine, startCol, endCol, textN)
    testSingleHighlight(codeN.replace('\n', '\r'), startPos, endPos)(
      startLine,
      endLine,
      startCol,
      endCol,
      textN.replace('\n', '\r')
    )
    val rnStartPos = startPos + codeN
      .substring(0, startPos.toInt)
      .map(f => if (f == '\n') 1 else 0)
      .sum
    val rnEndPos = endPos + codeN.substring(0, endPos.toInt).map(f => if (f == '\n') 1 else 0).sum
    testSingleHighlight(codeN.replace("\n", "\r\n"), rnStartPos, rnEndPos)(
      startLine,
      endLine,
      startCol,
      endCol,
      textN.replace("\n", "\r\n")
    )
  }

  private def translate1(): Unit = testHighlight("hello", 0, 5)(1, 0, 1, 5, "hello")
  private def translate2(): Unit = {
    // on Java an "; expected" error message has no text to return
    // should the first char of the next line be specified?
    testHighlight("foo", 3, 3)(1, 3, 1, 3, "")
  }
  private def translate3(): Unit = testHighlight("\nhello", 1, 1)(2, 0, 2, 0, "")
  private def translate4(): Unit = testHighlight("\nhello", 0, 0)(1, 0, 1, 0, "")
  private def translate5(): Unit = testHighlight("\nhello", 1, 5)(2, 0, 2, 4, "hell")
  private def translate6(): Unit = testHighlight("hello", 0, 1)(1, 0, 1, 1, "h")
  private def translate7(): Unit = testHighlight("h\n", 0, 1)(1, 0, 1, 1, "h")
  private def translate8(): Unit =
    testHighlight(
      "\npublic class Hello {\n    UnknownClass someVar;\n    public static void main(String[] args) {\n        System.out.println(\"Hello World\");\n    }\n}",
      26,
      38
    )(3, 4, 3, 16, "UnknownClass")
  private def translate9(): Unit =
    testHighlight(
      "\npublic class Hello {\n    UnknownClass someVar;\n    public static void main(String[] args) {\n        System.out.println(\"Hello World\");\n    }\n}",
      0,
      38
    )(1, 0, 3, 16, "\npublic class Hello {\n    UnknownClass")
  private def translate10(): Unit = testHighlight("\n\n\n", 0, 0)(1, 0, 1, 0, "")
  private def translate11(): Unit = testHighlight("\n\n\n", 1, 1)(2, 0, 2, 0, "")
  private def translate12(): Unit = testHighlight("", 0, 0)(1, 0, 1, 0, "")
  private def translate13(): Unit = testHighlight("foo", 0, 0)(1, 0, 1, 0, "")
  private def translate14(): Unit =
    testHighlight(
      "\n\n	protected void finalize() throws Throwable\n	{\n		try\n		{\n			dispose();\n		}\n		finally\n		{\n			super.finalize();\n		}\n	}\n	",
      3,
      118
    )(
      3,
      1,
      13,
      2,
      "protected void finalize() throws Throwable\n	{\n		try\n		{\n			dispose();\n		}\n		finally\n		{\n			super.finalize();\n		}\n	}"
    )
}
