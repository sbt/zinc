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

import java.util.Optional
import java.io.File
import javax.tools.{ Diagnostic, JavaFileObject, DiagnosticListener }
import sbt.io.IO
import sbt.util.InterfaceUtil.o2jo
import xsbti.{ Severity, Reporter }
import javax.tools.Diagnostic.NOPOS

/**
 * A diagnostics listener that feeds all messages into the given reporter.
 * @param reporter
 */
final class DiagnosticsReporter(reporter: Reporter) extends DiagnosticListener[JavaFileObject] {
  import DiagnosticsReporter._

  val END_OF_LINE_MATCHER = "(\r\n)|[\r]|[\n]"
  val EOL = System.getProperty("line.separator")

  private[this] var errorEncountered = false
  def hasErrors: Boolean = errorEncountered

  override def report(d: Diagnostic[? <: JavaFileObject]): Unit = {
    val severity: Severity = {
      d.getKind match {
        case Diagnostic.Kind.ERROR                                       => Severity.Error
        case Diagnostic.Kind.WARNING | Diagnostic.Kind.MANDATORY_WARNING => Severity.Warn
        case _                                                           => Severity.Info
      }
    }

    import sbt.util.InterfaceUtil.problem
    val msg = d.getMessage(null)
    val pos: xsbti.Position = PositionImpl(d)
    if (severity == Severity.Error) errorEncountered = true
    reporter.log(problem(
      cat = "",
      pos = pos,
      msg = msg,
      sev = severity,
      rendered = None,
      diagnosticCode = None,
      diagnosticRelatedInformation = Nil,
      actions = Nil,
    ))
  }
}

object DiagnosticsReporter {

  /**
   * Strict and immutable implementation of Position.
   */
  private[sbt] final class PositionImpl private (
      sourceUri: Option[String],
      override val line: Optional[Integer],
      override val lineContent: String,
      override val offset: Optional[Integer],
      override val pointer: Optional[Integer],
      override val pointerSpace: Optional[String],
      override val startOffset: Optional[Integer],
      override val endOffset: Optional[Integer],
      override val startLine: Optional[Integer],
      override val startColumn: Optional[Integer],
      override val endLine: Optional[Integer],
      override val endColumn: Optional[Integer]
  ) extends xsbti.Position {
    override val sourcePath: Optional[String] = o2jo(sourceUri)
    override val sourceFile: Optional[File] = o2jo(sourceUri.map(new File(_)))

    override def toString: String =
      if (sourceUri.isDefined) s"${sourceUri.get}:${if (line.isPresent) line.get else -1}"
      else ""
  }

  /**
   * VSCode documentation...
   * A range in a text document expressed as (zero-based) start and end positions.
   * A range is comparable to a selection in an editor.
   * Therefore the end position is exclusive.
   * If you want to specify a range that contains a line including the line ending character(s) then use an end position denoting the start of the next line.
   * Here the lines are 1-based as ZincInternals subtracts 1 later
   */
  def contentAndRanges(
      cc: CharSequence,
      start: Long,
      end: Long
  ): (Integer, Integer, Integer, Integer, String) = {

    var startPos = start.toInt
    var endPos = end.toInt

    val lineContent = cc.subSequence(startPos, endPos).toString
    // ignore CR or LF - depending on which one isn't found
    var checkForN = true
    var checkForR = true
    // find startLine and startColumn
    var startLine = 1
    var startColumn = 0
    startPos = startPos - 1
    while (startPos >= 0) {
      val ch = cc.charAt(startPos)
      if (checkForR && ch == '\r') {
        startLine = startLine + 1
        checkForN = false
      } else if (checkForN && ch == '\n') {
        startLine = startLine + 1
        checkForR = false
      } else if (startLine == 1)
        startColumn = startColumn + 1

      startPos = startPos - 1
    }
    // find endLine and endColumn
    var endLine = startLine
    var endColumn = 0
    endPos = endPos - 1
    while (endPos >= start) {
      // mimic linefeed if at the end of the file
      if (endPos == cc.length)
        endLine = endLine + 1
      else {
        val ch = cc.charAt(endPos)
        if (checkForR && ch == '\r') {
          endLine = endLine + 1
          checkForN = false
        } else if (checkForN && ch == '\n') {
          endLine = endLine + 1
          checkForR = false
        } else if (endLine == startLine)
          endColumn = endColumn + 1
      }

      endPos = endPos - 1
    }
    if (startLine == endLine)
      endColumn = endColumn + startColumn

    (startLine, startColumn, endLine, endColumn, lineContent)
  }

  private[sbt] object PositionImpl {

    /**
     * Extracts PositionImpl from a Java Diagnostic.
     * The previous implementation of PositionImpl held on to the Diagnostic object as a wrapper,
     * and calculated the lineContent on the fly.
     * This caused a race condition on the Diagnostic object, resulting in a NullPointerException.
     * See https://github.com/sbt/sbt/issues/3623
     */
    def apply(d: Diagnostic[? <: JavaFileObject]): PositionImpl = {
      // https://docs.oracle.com/javase/7/docs/api/javax/tools/Diagnostic.html
      // Negative values (except NOPOS) and 0 are not valid line or column numbers,
      // except that you can cause this number to occur by putting "abc {}" in A.java.
      // This will cause Invalid position: 0 masking the actual error message
      //     a/A.java:1: class, interface, or enum expected
      def checkNoPos(n: Long): Option[Long] =
        n match {
          case NOPOS       => None
          case x if x <= 0 => None
          case x           => Option(x)
        }

      val source: Option[JavaFileObject] = Option(d.getSource)

      // see also LocalJava.scala
      val sourcePath: Option[String] =
        source match {
          case Some(obj) =>
            val uri = obj.toUri
            if (uri.getScheme == "file")
              Some(
                IO.urlAsFile(uri.toURL)
                  .map(_.getAbsolutePath)
                  .getOrElse(uri.toString)
              )
            else if (uri.getScheme == "vf")
              Some(LocalJava.fromUri(uri).id)
            else Some(uri.toString)
          case _ => None
        }

      def startPosition: Option[Long] = checkNoPos(d.getStartPosition)
      def endPosition: Option[Long] = checkNoPos(d.getEndPosition)

      val line: Optional[Integer] = o2jo(checkNoPos(d.getLineNumber) map (_.toInt))
      // column number is 1-based, xsbti.Position#pointer is 0-based.
      val pointer: Optional[Integer] = o2jo(checkNoPos(d.getColumnNumber - 1) map (_.toInt))
      val offset: Optional[Integer] = o2jo(checkNoPos(d.getPosition) map (_.toInt))
      val startOffset: Optional[Integer] = o2jo(startPosition map (_.toInt))
      val endOffset: Optional[Integer] = o2jo(endPosition map (_.toInt))

      def noPositionInfo
          : (Optional[Integer], Optional[Integer], Optional[Integer], Optional[Integer], String) =
        if (line.isPresent)
          (line, o2jo(Some(0)), Optional.of(line.get() + 1), o2jo(Some(0)), "")
        else
          (
            o2jo(Option.empty[Integer]),
            o2jo(Option.empty[Integer]),
            o2jo(Option.empty[Integer]),
            o2jo(Option.empty[Integer]),
            ""
          )

      // TODO - Is this pulling contents of the line correctly?
      // Would be ok to just return null if this version of the JDK doesn't support grabbing
      // source lines?
      val (startLine, startColumn, endLine, endColumn, lineContent) =
        source match {
          case Some(source: JavaFileObject) =>
            (Option(source.getCharContent(true)), startPosition, endPosition) match {
              case (Some(cc), Some(start), Some(end))
                  // Guard against Javac bug in parsing `public class ChrisTest { void m() { else null; }}`
                  if end >= start =>
                // can't optimise using line as it's not always the same as startLine
                val range = contentAndRanges(cc, start, end)
                (
                  o2jo(Option(range._1)),
                  o2jo(Option(range._2)),
                  o2jo(Option(range._3)),
                  o2jo(Option(range._4)),
                  range._5
                )
              case _ => noPositionInfo
            }
          case _ => noPositionInfo
        }

      val pointerSpace = pointer.map[String] { p =>
        lineContent.toList.take(p.intValue()).map {
          case '\t' => '\t'
          case _    => ' '
        }.mkString
      }

      new PositionImpl(
        sourcePath,
        line,
        lineContent,
        offset,
        pointer,
        pointerSpace,
        startOffset,
        endOffset,
        startLine,
        startColumn,
        endLine,
        endColumn
      )
    }
  }
}
