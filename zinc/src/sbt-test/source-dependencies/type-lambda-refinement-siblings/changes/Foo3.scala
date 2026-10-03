package test

trait Two[F[_], G[_]] {
  def f: F[Int]
  def g: G[String]
}

class X[A]
class Y[A]
class H[F[_]]

trait Foo
    extends Two[
      ({ type l[a] = H[({ type m[b] = Map[b, a] })#m] })#l,
      ({ type l[a] = Y[a] })#l
    ]
