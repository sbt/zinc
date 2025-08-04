trait C extends B {
  def x = something
}
trait B extends A {
  def something = buildNonemptyObjects(5)
}
