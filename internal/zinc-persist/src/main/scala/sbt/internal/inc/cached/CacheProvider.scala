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

package sbt.internal.inc.cached

import xsbti.compile.{ CompileAnalysis, MiniSetup }

trait CacheProvider {
  def findCache(previous: Option[(CompileAnalysis, MiniSetup)]): Option[CompilationCache]
}
