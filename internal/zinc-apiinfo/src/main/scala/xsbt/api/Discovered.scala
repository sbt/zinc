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

package xsbt.api

final case class Discovered(baseClasses: Set[String],
                            annotations: Set[String],
                            hasMain: Boolean,
                            isModule: Boolean) {
  def isEmpty = baseClasses.isEmpty && annotations.isEmpty
}
object Discovered {
  def empty = new Discovered(Set.empty, Set.empty, false, false)
}
