package p
object X {
  def main(args: Array[String]): Unit = assert(implicitly[Show[List[C]]].show == args(0))
}
