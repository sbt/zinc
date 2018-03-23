/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import xsbti.compile.analysis

case class PositionName private (line: Int, column: Int, name: String, fullName: String)
    extends analysis.PositionName

object PositionName {

  def of(line: Int, column: Int, name: String, fullName: String): PositionName = {
    PositionName(line, column, escapeControlChars(name), escapeControlChars(fullName))
  }

  private def escapeControlChars(name: String) = {
    name.replaceAllLiterally("\n", "\u26680A")
  }
}
