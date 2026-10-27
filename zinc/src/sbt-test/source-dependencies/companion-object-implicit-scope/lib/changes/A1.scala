trait A

object A {
  implicit val sad: Pretty[A] = new Pretty[A] { def show(t: A) = "a" }
  def y: Int = 2
}
