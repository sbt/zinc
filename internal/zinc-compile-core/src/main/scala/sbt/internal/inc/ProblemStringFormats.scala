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

import xsbti.Problem
import sbt.util.ShowLines
import sbt.util.InterfaceUtil.jo2o

/**
 * Represent a string that contains the compiler output (warnings and error
 * messages, etc) that have been reported by [[LoggedReporter]] and the logger.
 */
trait ProblemStringFormats {
  implicit lazy val ProblemStringFormat: ShowLines[Problem] = new ShowLines[Problem] {
    def showLines(p: Problem): Seq[String] =
      if (p.rendered.isPresent)
        Vector(p.rendered.get)
      else if (!p.position.sourcePath.isPresent && !p.position.line.isPresent)
        Vector(p.message)
      else {
        val pos = p.position
        val sourcePrefix = jo2o(pos.sourcePath).getOrElse("")
        val columnNumber = jo2o(pos.pointer).fold(1)(_.toInt + 1)
        val lineNumberString = jo2o(pos.line).fold(":")(":" + _ + ":" + columnNumber + ":") + " "
        val line1 = sourcePrefix + lineNumberString + p.message
        val lineContent = pos.lineContent
        if (!lineContent.isEmpty) {
          Vector(line1, lineContent) ++
            (for {
              space <- jo2o(pos.pointerSpace)
            } yield (space + "^")).toVector // pointer to the column position of the error/warning
        } else Vector(line1)
      }
  }
}
