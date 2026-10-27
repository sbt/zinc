package p
class A
object A {
  implicit def sa[T <: A]: Show[T] = Show("A")
}
