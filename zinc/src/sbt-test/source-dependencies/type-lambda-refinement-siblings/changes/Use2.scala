package test

object Use {
  def f(foo: Foo): H[({ type m[b] = Map[Int, b] })#m] = foo.f
  def g(foo: Foo): Y[String] = foo.g
}
