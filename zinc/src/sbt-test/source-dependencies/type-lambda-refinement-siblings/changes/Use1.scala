package test

object Use {
  def f(foo: Foo): Y[Int] = foo.f
  def g(foo: Foo): X[String] = foo.g
}
