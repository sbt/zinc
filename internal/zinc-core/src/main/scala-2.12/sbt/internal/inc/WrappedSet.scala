/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc

import xsbti.VirtualFileRef
import scala.collection.immutable.Set

private[inc] class WrappedSet(s: java.util.Set[VirtualFileRef]) extends Set[VirtualFileRef] {
  import scala.jdk.CollectionConverters._
  def iterator: Iterator[xsbti.VirtualFileRef] = s.asScala.iterator
  def contains(elem: xsbti.VirtualFileRef): Boolean = s.contains(elem)

  def +(elem: xsbti.VirtualFileRef): Set[xsbti.VirtualFileRef] =
    s.asScala.foldLeft(Set(elem)) { case (a, e) => a + e }
  def -(elem: xsbti.VirtualFileRef): Set[xsbti.VirtualFileRef] =
    s.asScala.foldLeft(Set.empty[VirtualFileRef]) {
      case (a, e) => if (e != elem) a + e else a
    }
}
