/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc

import scala.collection.immutable.Set
import xsbti.VirtualFileRef

private[inc] class WrappedSet(s: java.util.Set[VirtualFileRef]) extends Set[VirtualFileRef] {
  import scala.jdk.CollectionConverters._
  def iterator: Iterator[VirtualFileRef] = s.asScala.iterator
  def contains(elem: VirtualFileRef): Boolean = s.contains(elem)
  def excl(elem: VirtualFileRef): Set[VirtualFileRef] =
    s.asScala.foldLeft(Set.empty[VirtualFileRef]) { case (a, e) => if (e != elem) a + e else a }
  def incl(elem: VirtualFileRef): Set[VirtualFileRef] =
    s.asScala.foldLeft(Set(elem)) { case (a, e) => a + e }
}
