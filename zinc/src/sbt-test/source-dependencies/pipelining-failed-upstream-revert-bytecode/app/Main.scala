package p
object Main {
  def main(args: Array[String]): Unit = {
    val ret = classOf[C].getMethod("f", classOf[A]).getReturnType
    assert(ret == classOf[Int], ret)
  }
}
