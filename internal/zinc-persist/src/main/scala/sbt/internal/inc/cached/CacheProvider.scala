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

package sbt.internal.inc.cached

import xsbti.compile.{ CompileAnalysis, MiniSetup }

trait CacheProvider {
  def findCache(previous: Option[(CompileAnalysis, MiniSetup)]): Option[CompilationCache]
}
