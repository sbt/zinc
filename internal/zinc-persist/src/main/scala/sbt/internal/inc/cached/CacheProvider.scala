/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.cached

import xsbti.compile.{ CompileAnalysis, MiniSetup }

trait CacheProvider {
  def findCache(previous: Option[(CompileAnalysis, MiniSetup)]): Option[CompilationCache]
}
