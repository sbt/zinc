package p
class A
object A {
  implicit def sa[T <: A]: Show[List[T]] = Show("A")
}
