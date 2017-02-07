/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt.api

final case class Discovered(baseClasses: Set[String], annotations: Set[String], hasMain: Boolean, isModule: Boolean) {
  def isEmpty = baseClasses.isEmpty && annotations.isEmpty
}
object Discovered {
  def empty = new Discovered(Set.empty, Set.empty, false, false)
}