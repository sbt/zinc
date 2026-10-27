package test

trait MonadReader[F[_], R] {
  def ask: F[R]
  def local[A](f: R => R)(fa: F[A]): F[A]
}
