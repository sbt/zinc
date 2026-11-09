package p
object X {
  def main(args: Array[String]): Unit = assert(implicitly[Show[O.type]].show == args(0))
}
