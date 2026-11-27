object Main {
  def main(args: Array[String]): Unit = {
    val s = new D().foo
    assert(s == args(0), s)
  }
}
