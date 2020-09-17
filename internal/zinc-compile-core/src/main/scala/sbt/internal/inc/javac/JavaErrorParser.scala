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

import java.io.File

import java.util.Optional
import sbt.util.InterfaceUtil.o2jo
import xsbti.{ Problem, Severity, Position }

/** A wrapper around xsbti.Position so we can pass in Java input. */
final case class JavaPosition(_sourceFilePath: String, _line: Int, _contents: String, _offset: Int)
    extends Position {
  def line: Optional[Integer] = o2jo(Some(_line))
  def lineContent: String = _contents
  def offset: Optional[Integer] = o2jo(Some(_offset))
  def pointer: Optional[Integer] = o2jo(None)
  def pointerSpace: Optional[String] = o2jo(None)
  def sourcePath: Optional[String] = o2jo(Option(_sourceFilePath))
  def sourceFile: Optional[File] = o2jo(Option(new File(_sourceFilePath)))
  override def toString = s"${_sourceFilePath}:${_line}:${_offset}"
}

/** A position which has no information, because there is none. */
object JavaNoPosition extends Position {
  def line: Optional[Integer] = o2jo(None)
  def lineContent: String = ""
  def offset: Optional[Integer] = o2jo(None)
  def pointer: Optional[Integer] = o2jo(None)
  def pointerSpace: Optional[String] = o2jo(None)
  def sourcePath: Optional[String] = o2jo(None)
  def sourceFile: Optional[File] = o2jo(None)
  override def toString = "NoPosition"
}

/** A wrapper around xsbti.Problem with java-specific options. */
final case class JavaProblem(position: Position, severity: Severity, message: String)
    extends xsbti.Problem {
  override def category: String =
    "javac" // TODO - what is this even supposed to be?  For now it appears unused.
  override def toString = s"$severity @ $position - $message"
}

/** A parser that is able to parse java's error output successfully. */
class JavaErrorParser(relativeDir: File = new File(new File(".").getAbsolutePath).getCanonicalFile)
    extends scala.util.parsing.combinator.RegexParsers {
  // Here we track special handlers to catch "Note:" and "Warning:" lines.
  private val NOTE_LINE_PREFIXES = Array("Note: ", "\u6ce8: ", "\u6ce8\u610f\uff1a ")
  private val WARNING_PREFIXES = Array("warning", "\u8b66\u544a", "\u8b66\u544a\uff1a")

  override val skipWhitespace = false

  val JAVAC: Parser[String] = literal("javac")
  val SEMICOLON: Parser[String] = literal(":") | literal("\uff1a")

  // We ignore whether it actually says "symbol" for i18n
  val SYMBOL: Parser[String] = allUntilChar(':')
  // We ignore whether it actually says "location" for i18n.
  val LOCATION: Parser[String] = allUntilChar(':')

  val WARNING: Parser[String] = allUntilChar(':') ^? {
    case x if WARNING_PREFIXES.exists(x.trim.startsWith) => x
  }
  // Parses the rest of an input line.
  val restOfLine: Parser[String] =
    // TODO - Can we use END_OF_LINE here without issues?
    allUntilChars(Array('\n', '\r')) ~ """[\r]?[\n]?""".r ^^ { case msg ~ _ =>
      msg
    }
  val NOTE: Parser[String] = restOfLine ^? {
    case x if NOTE_LINE_PREFIXES exists x.startsWith => x
  }
  val allIndented: Parser[String] =
    rep("""\s+""".r ~ restOfLine ^^ { case x ~ msg =>
      x + msg
    }) ^^ { case xs =>
      xs.mkString("\n")
    }
  val nonPathLine: Parser[String] = {
    val nonPathLine0 = new Parser[String] {
      def isStopChar(c: Char): Boolean = c == '\n' || c == '\r'

      def apply(in: Input) = {
        val source = in.source
        val offset = in.offset
        var i = offset
        while (i < source.length && !isStopChar(source.charAt(i))) {
          i += 1
        }
        val line = source.subSequence(offset, i).toString
        if ((line.startsWith("/") || line.contains("\\")) && line.contains(".java"))
          Failure("Path found", in)
        else if (i == offset) Failure("Empty", in)
        else Success(line, in.drop(i - offset))
      }
    }
    nonPathLine0 ~ """[\r]?[\n]?""".r ^^ { case msg ~ endline =>
      msg + endline
    }
  }
  val nonPathLines: Parser[String] = {
    rep(nonPathLine) ^^ { case lines =>
      lines.mkString("")
    }
  }

  // Parses ALL characters until an expected character is met.
  def allUntilChar(c: Char): Parser[String] = allUntilChars(Array(c))
  def allUntilChars(chars: Array[Char]): Parser[String] = new Parser[String] {
    def isStopChar(c: Char): Boolean = {
      var i = 0
      while (i < chars.length) {
        if (c == chars(i)) return true
        i += 1
      }
      false
    }

    def apply(in: Input) = {
      val source = in.source
      val offset = in.offset
      val start = handleWhiteSpace(source, offset)
      var i = start
      while (i < source.length && !isStopChar(source.charAt(i))) {
        i += 1
      }
      Success(source.subSequence(start, i).toString, in.drop(i - offset))
    }
  }

  // Helper to extract an integer from a string
  private object ParsedInteger {
    def unapply(s: String): Option[Int] =
      try Some(Integer.parseInt(s))
      catch { case _: NumberFormatException => None }
  }
  // Parses a line number
  val line: Parser[Int] = allUntilChar(':') ^? { case ParsedInteger(x) =>
    x
  }

  // Parses the file + lineno output of javac.
  val fileAndLineNo: Parser[(String, Int)] = {
    val linuxFile = allUntilChar(':') ^^ { _.trim() }
    val windowsRootFile = linuxFile ~ SEMICOLON ~ linuxFile ^^ { case root ~ _ ~ path =>
      s"$root:$path"
    }
    val linuxOption = linuxFile ~ SEMICOLON ~ line ^^ { case f ~ _ ~ l => (f, l) }
    val windowsOption = windowsRootFile ~ SEMICOLON ~ line ^^ { case f ~ _ ~ l => (f, l) }
    (linuxOption | windowsOption)
  }

  val allUntilCaret: Parser[String] = allUntilChar('^')

  // Helper method to try to handle relative vs. absolute file pathing....
  // NOTE - this is probably wrong...
  private def findFileSource(f: String): String = {
    // If a file looks like an absolute path, leave it as is.
    def isAbsolute(f: String) =
      (f startsWith "/") || (f matches """[^\\]+:\\.*""")
    // TODO - we used to use existence checks, that may be the right way to go
    if (isAbsolute(f)) f
    else (new File(relativeDir, f)).getAbsolutePath
  }

  /** Parses an error message (not this WILL parse warning messages as error messages if used incorrectly. */
  val errorMessage: Parser[Problem] = {
    val fileLineMessage = fileAndLineNo ~ SEMICOLON ~ restOfLine ^^ { case (file, line) ~ _ ~ msg =>
      (file, line, msg)
    }
    fileLineMessage ~ (allUntilCaret ~ '^' ~ restOfLine).? ~ (nonPathLines.?) ^^ {
      case (file, line, msg) ~ contentsOpt ~ ind =>
        new JavaProblem(
          new JavaPosition(
            findFileSource(file),
            line,
            (contentsOpt match {
              case Some(contents ~ _ ~ r) => contents + '^' + r
              case _                      => ""
            }) + ind
              .getOrElse(""), // TODO - Actually parse caret position out of here.
            (contentsOpt match {
              case Some(contents ~ _ ~ _) => getOffset(contents)
              case _                      => 0
            })
          ),
          Severity.Error,
          msg
        )
    }
  }

  /** Parses javac warning messages. */
  val warningMessage: Parser[Problem] = {
    val fileLineMessage = fileAndLineNo ~ SEMICOLON ~ WARNING ~ SEMICOLON ~ restOfLine ^^ {
      case (file, line) ~ _ ~ _ ~ _ ~ msg => (file, line, msg)
    }
    fileLineMessage ~ (allUntilCaret ~ '^' ~ restOfLine).? ~ (nonPathLines.?) ^^ {
      case (file, line, msg) ~ contentsOpt ~ ind =>
        new JavaProblem(
          new JavaPosition(
            findFileSource(file),
            line,
            (contentsOpt match {
              case Some(contents ~ _ ~ r) => contents + '^' + r
              case _                      => ""
            }) + ind.getOrElse(""),
            (contentsOpt match {
              case Some(contents ~ _ ~ _) => getOffset(contents)
              case _                      => 0
            })
          ),
          Severity.Warn,
          msg
        )
    }
  }
  val noteMessage: Parser[Problem] =
    NOTE ^^ { msg =>
      new JavaProblem(
        JavaNoPosition,
        Severity.Info,
        msg
      )
    }
  val javacError: Parser[Problem] =
    JAVAC ~ SEMICOLON ~ restOfLine ^^ { case _ ~ _ ~ error =>
      new JavaProblem(
        JavaNoPosition,
        Severity.Error,
        s"javac:$error"
      )
    }

  val outputSumamry: Parser[Unit] =
    """(\s*)(\d+) (\w+)""".r ~ restOfLine ^^ { case a ~ b =>
      ()
    }

  val potentialProblem: Parser[Problem] = warningMessage | errorMessage | noteMessage | javacError

  val javacOutput: Parser[Seq[Problem]] = rep(potentialProblem) <~ opt(outputSumamry)

  /**
   * Example:
   *
   * Test.java:4: cannot find symbol
   * symbol  : method baz()
   * location: class Foo
   * return baz();
   * ^
   *
   * Test.java:8: warning: [deprecation] RMISecurityException(java.lang.String) in java.rmi.RMISecurityException has been deprecated
   * throw new java.rmi.RMISecurityException("O NOES");
   * ^
   */
  final def parseProblems(in: String, logger: sbt.util.Logger): Seq[Problem] =
    parse(javacOutput, in) match {
      case Success(result, next) =>
        if (!next.atEnd) {
          logger.warn(s"Unexpected javac output: ${next.source}.")
        }
        result
      case Failure(msg, n) =>
        logger.warn(s"Unexpected javac output at ${n.pos.longString}: $msg.")
        Seq.empty
      case Error(msg, n) =>
        logger.warn(s"Unexpected javac output at ${n.pos.longString}: $msg.")
        Seq.empty
    }

  private def getOffset(contents: String): Int =
    contents.linesIterator.toList.lastOption map (_.length) getOrElse 0

}
