package app

object App {
  def main(args: Array[String]): Unit = {
    val res = lib.Macro.append("ABC")
    val exp = args(0)
    if (res != exp) {
      val e = new Exception(s"assertion failed: expected $exp, obtained $res")
      e.setStackTrace(Array())
      throw e
    }
  }
}
