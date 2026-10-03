package test

object Use {
  def f(foo: Foo): X[Int] = foo.f
  def g(foo: Foo): Y[String] = foo.g
}
