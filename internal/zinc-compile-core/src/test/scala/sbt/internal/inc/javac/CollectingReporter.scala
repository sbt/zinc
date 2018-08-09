package sbt
package internal
package inc
package javac

import xsbti.Reporter
import xsbti.Problem

class CollectingReporter extends Reporter {
  var problems: Array[Problem] = Array[Problem]()

  def reset() = problems = Array[Problem]()
  def hasErrors() = ???
  def hasWarnings(): Boolean = ???
  def printSummary(): Unit = ???
  def log(problem: xsbti.Problem): Unit = problems :+= problem
  def comment(pos: xsbti.Position, msg: String): Unit = ???

}
