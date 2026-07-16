package foo

object Test {
  def test(implicit x: Int = 1): String = x.toString
  def main(args: Array[String]): Unit = assert(test == "1")
}
