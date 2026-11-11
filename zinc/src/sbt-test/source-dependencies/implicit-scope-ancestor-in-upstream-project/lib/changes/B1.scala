package p
class B extends A
object B {
  implicit def sb[T <: B]: Show[T] = Show("B")
}
