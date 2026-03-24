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

package sbt
package internal
package inc

import xsbti.{ Reporter, Logger => xLogger }
import xsbti.compile._

final class FreshCompilerCache extends GlobalsCache {
  def clear(): Unit = ()
  override def apply(
      args: Array[String],
      output: Output,
      forceNew: Boolean,
      c: CachedCompilerProvider,
      log: xLogger,
      reporter: Reporter
  ): CachedCompiler = c.newCachedCompiler(args, output, log, reporter)
}
