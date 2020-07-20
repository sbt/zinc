package sbt.internal.inc

import scala.collection.immutable.Set
import xsbti.VirtualFileRef

private[inc] class WrappedSet(s: java.util.HashSet[VirtualFileRef]) extends Set[VirtualFileRef] {
  import scala.jdk.CollectionConverters._
  def iterator: Iterator[VirtualFileRef] = s.asScala.iterator
  def contains(elem: VirtualFileRef): Boolean = s.contains(s)
  def excl(elem: VirtualFileRef): Set[VirtualFileRef] =
    s.asScala.foldLeft(Set.empty[VirtualFileRef]) { case (a, e) => if (e != elem) a + e else a }
  def incl(elem: VirtualFileRef): Set[VirtualFileRef] =
    s.asScala.foldLeft(Set(elem)) { case (a, e) => a + e }
}
