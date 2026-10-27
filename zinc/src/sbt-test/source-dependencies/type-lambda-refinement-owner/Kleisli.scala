package test

case class Kleisli[F[_], R, A](run: R => F[A])

trait KleisliMonadReader[F[_], R]
    extends MonadReader[({ type l[a] = Kleisli[F, R, a] })#l, R] {
  def ask: Kleisli[F, R, R] = ???
  def local[A](f: R => R)(fa: Kleisli[F, R, A]): Kleisli[F, R, A] = ???
}
