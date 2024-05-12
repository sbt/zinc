class B(val z: Int) {
  def this(x: Int, y: String = "") = this(x + y.toInt)
}

object B {
  val z = 1
}