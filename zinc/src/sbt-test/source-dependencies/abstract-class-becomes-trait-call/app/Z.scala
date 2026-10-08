package p
object Z {
  def use0[U](x: A[U]): Int = x.m
  def main(args: Array[String]): Unit = assert(use0(new AImpl) == 1)
}
