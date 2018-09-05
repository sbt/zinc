package foo

trait Foo {
  def use(bar: Foo.Bar): Any
}

object Foo {
  class Bar(
      a: Int = 0,
  )
}
