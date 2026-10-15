object Main {
  def main(args: Array[String]): Unit = {
    val uid = java.io.ObjectStreamClass.lookup(classOf[Foo]).getSerialVersionUID
    assert(uid == args(0).toLong, uid)
  }
}
