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

package xsbt

import java.io.File
import java.util.Optional

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.reporting.{ AbstractReporter, Diagnostic }
import dotty.tools.dotc.interfaces
import dotty.tools.dotc.util.{ SourceFile, SourcePosition }
import xsbti.{ Position, Problem, Severity }

private final class DelegatingReporter(
    private[this] var delegate: xsbti.Reporter
) extends AbstractReporter {
  import DelegatingReporter._
  def dropDelegate(): Unit = { delegate = null }

  override def printSummary(using ctx: Context): Unit = delegate.printSummary()

  override def doReport(diagnostic: Diagnostic)(using Context): Unit = {
    val severity = convert(diagnostic.level)
    val position = convert(diagnostic.pos)
    val message = diagnostic.msg
    val explain = if (Diagnostic.shouldExplain(diagnostic) && message.explanation.nonEmpty) {
      explanation(message)
    } else ""
    val rendered = messageAndPos(message, diagnostic.pos, diagnosticLevel(diagnostic)) + explain
    delegate.log(CompileProblem(position, message.message, severity, rendered))
  }
}

private object DelegatingReporter {
  private def convert(pos: SourcePosition): Position = {
    if (pos.exists) new PositionImpl(pos, pos.source)
    else EmptyPosition
  }

  private def convert(level: Int): xsbti.Severity = {
    level match {
      case interfaces.Diagnostic.ERROR   => Severity.Error
      case interfaces.Diagnostic.WARNING => Severity.Warn
      case interfaces.Diagnostic.INFO    => Severity.Info
      case level                         => throw new IllegalArgumentException(s"Bad diagnostic level: $level")
    }
  }

  object EmptyPosition extends xsbti.Position {
    override def line(): Optional[Integer] = Optional.empty
    override def lineContent(): String = ""
    override def offset(): Optional[Integer] = Optional.empty
    override def pointer(): Optional[Integer] = Optional.empty
    override def pointerSpace(): Optional[String] = Optional.empty
    override def sourcePath(): Optional[String] = Optional.empty
    override def sourceFile(): Optional[File] = Optional.empty
  }

  class PositionImpl(
      pos: SourcePosition,
      src: SourceFile
  ) extends xsbti.Position {
    def line: Optional[Integer] = {
      if (src.content.isEmpty)
        Optional.empty
      else {
        val line = pos.line
        if (line == -1) Optional.empty
        else Optional.of(line + 1)
      }
    }
    def lineContent: String = {
      if (src.content.isEmpty) ""
      else {
        val line = pos.lineContent
        if (line.endsWith("\r\n"))
          line.substring(0, line.length - 2)
        else if (line.endsWith("\n") || line.endsWith("\u000c"))
          line.substring(0, line.length - 1)
        else line
      }
    }
    def offset: Optional[Integer] = Optional.of(pos.point)
    def sourcePath: Optional[String] = {
      if (!src.exists) Optional.empty
      else Optional.ofNullable(src.file.path)
    }
    def sourceFile: Optional[File] = {
      if (!src.exists) Optional.empty
      else Optional.ofNullable(src.file.file)
    }
    def pointer: Optional[Integer] = {
      if (src.content.isEmpty) Optional.empty
      else Optional.of(pos.point - src.startOfLine(pos.point))
    }
    def pointerSpace: Optional[String] = {
      if (src.content.isEmpty) Optional.empty
      else {
        // Don't crash if pointer is out-of-bounds (happens with some macros)
        val fixedPointer = Math.min(pointer.get, lineContent.length)
        val result = lineContent
          .take(fixedPointer)
          .map {
            case '\t' => '\t'
            case _    => ' '
          }
        Optional.of(result)
      }
    }
  }

  private final case class CompileProblem(
      position: Position,
      message: String,
      severity: Severity,
      _rendered: String
  ) extends Problem {
    override def category = ""
    override def rendered: Optional[String] = Optional.of(_rendered)
  }
}
