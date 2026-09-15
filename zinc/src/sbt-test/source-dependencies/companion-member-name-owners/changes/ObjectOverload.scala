class P { def over(a: Any): String = "" }

trait T { def inherited: Int = 0 }

class A { def x: Int = 1 }

object A extends P with T {
  def x: String = ""
  def z: Int = 2
  def over(i: Int): Int = 1
}
