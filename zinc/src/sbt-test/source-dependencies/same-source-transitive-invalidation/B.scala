object C {
  def x = B.something
}

object B {
  def something = A.buildNonemptyObjects(5)
}
