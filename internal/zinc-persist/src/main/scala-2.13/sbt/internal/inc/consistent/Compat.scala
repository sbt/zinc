package sbt.internal.inc.consistent

object Compat {
  type Factory[-A, +C] = scala.collection.Factory[A, C]
}
