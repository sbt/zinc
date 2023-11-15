package app

object App {
  def main(args: Array[String]): Unit = {
    val res = lib.Macro.append("ABC")
    val exp = args(0)
    assert(res == exp, s"Expected $exp, obtained $res")
  }
}
