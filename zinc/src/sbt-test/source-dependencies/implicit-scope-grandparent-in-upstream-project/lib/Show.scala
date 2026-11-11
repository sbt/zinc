package p
trait Show[T] { def show: String }
object Show {
  def apply[T](s: String): Show[T] = new Show[T] { def show = s }
  implicit def default[T]: Show[T] = Show("default")
}
