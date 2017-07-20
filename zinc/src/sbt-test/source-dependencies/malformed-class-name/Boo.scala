package repro

abstract class Boo {
  // Pretend use of `Boo`'s companion
  val b = Boo
}

object Boo {
  object Foo {
    class Impl
  }
}
