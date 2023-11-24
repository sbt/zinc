package app

import Macros._

object App {
  def main(args: Array[String]): Unit = {
    val expected = args(0).toBoolean
    val actual = Macros.hasAnyField[A]
    assert(expected == actual, s"Expected $expected, obtained $actual")
  }
}