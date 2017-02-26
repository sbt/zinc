package sbt
package internal
package inc

import xsbti.Problem
import sbt.util.ShowLines
import sbt.util.InterfaceUtil.jo2o

/**
 * Represent a string that contains the compiler output (warnings and error
 * messages, etc) that have been reported by [[LoggerReporter]] and the logger.
 */
trait ProblemStringFormats {
  implicit lazy val ProblemStringFormat: ShowLines[Problem] = new ShowLines[Problem] {
    def showLines(p: Problem): Seq[String] =
      p match {
        case p if !p.position.sourcePath.isPresent && !p.position.line.isPresent => Vector(p.message)
        case _ =>
          val pos = p.position
          val sourcePrefix = jo2o(pos.sourcePath).getOrElse("")
          val columnNumber = jo2o(pos.pointer).map(_.toInt + 1).getOrElse(1)
          val lineNumberString = jo2o(pos.line).map(":" + _ + ":" + columnNumber + ":").getOrElse(":") + " "
          val line1 = sourcePrefix + lineNumberString + p.message
          val lineContent = pos.lineContent
          if (!lineContent.isEmpty) {
            Vector(line1, lineContent) ++
              (for { space <- jo2o(pos.pointerSpace) }
                yield (space + "^")).toVector // pointer to the column position of the error/warning
          } else Vector(line1)
      }
  }
}
