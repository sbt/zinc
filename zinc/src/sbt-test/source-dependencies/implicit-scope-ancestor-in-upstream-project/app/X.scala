package p
object X {
  def main(args: Array[String]): Unit = assert(implicitly[Show[C]].show == args(0))
}
