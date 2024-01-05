package sbt.internal.inc.consistent

import java.util.Arrays
import scala.collection.{ MapLike, SetLike, SortedMap, SortedMapLike }
import scala.collection.generic.{
  CanBuildFrom,
  GenericTraversableTemplate,
  MapFactory,
  SeqFactory,
  SetFactory,
  SortedMapFactory
}

// some simple compatibility shims for 2.12 so we don't need to depend on collection-compat
object Compat {
  type Factory[-A, +C] = CanBuildFrom[Nothing, A, C]

  implicit def sortedMapFactoryToCBF[CC[A, B] <: SortedMap[A, B] with SortedMapLike[
    A,
    B,
    CC[A, B]
  ], K: Ordering, V](f: SortedMapFactory[CC]): Factory[(K, V), CC[K, V]] =
    new f.SortedMapCanBuildFrom

  implicit def mapFactoryToCBF[CC[A, B] <: Map[A, B] with MapLike[A, B, CC[A, B]], K, V](
      f: MapFactory[CC]
  ): Factory[(K, V), CC[K, V]] =
    new f.MapCanBuildFrom

  implicit def seqFactoryToCBF[CC[X] <: Seq[X] with GenericTraversableTemplate[X, CC], E](
      f: SeqFactory[CC]
  ): Factory[E, CC[E]] =
    new f.GenericCanBuildFrom

  implicit def setFactoryToCBF[CC[X] <: Set[X] with SetLike[X, CC[X]], E](f: SetFactory[CC])
      : Factory[E, CC[E]] =
    f.setCanBuildFrom

  implicit class FactoryOps[-A, +C](private val factory: Factory[A, C]) {
    def newBuilder: scala.collection.mutable.Builder[A, C] = factory()
  }

  type IterableOnce[+E] = TraversableOnce[E]

  implicit class IterableOnceOps[+E](private val it: IterableOnce[E]) {
    def iterator: Iterator[E] = it match {
      case it: Iterator[_] => it.asInstanceOf[Iterator[E]]
      case it              => it.asInstanceOf[Iterable[E]].iterator
    }
  }

  implicit class ArrayOps[A <: AnyRef](private val a: Array[A]) {
    def sortInPlaceBy[B](f: A => B)(implicit ord: Ordering[B]): Unit = Arrays.sort(a, ord on f)
  }
}
