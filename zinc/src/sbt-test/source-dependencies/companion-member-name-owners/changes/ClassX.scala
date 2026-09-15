class P { def over(a: Any): String = "" }

trait T { def inherited: Int = 0 }

class A { def x: String = "" }

object A extends P with T {
  def x: String = ""
  def z: Int = 2
}
