class B(val z: Int) {
  def this(x: Int, y: Int = 2) = this(x + y)
}

object B {
  val z = 1
}