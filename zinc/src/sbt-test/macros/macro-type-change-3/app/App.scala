package app

import Macros._
import A.B

object App {
  def main(args: Array[String]): Unit = {
    val expected = args(0).toBoolean
    val actual = Macros.hasAnyField[B]
    assert(expected == actual, s"Expected $expected, obtained $actual")
  }
}