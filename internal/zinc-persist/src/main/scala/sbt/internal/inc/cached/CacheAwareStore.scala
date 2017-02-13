/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.cached

import java.io.File

import sbt.internal.inc.AnalysisStore
import xsbti.compile.{ MiniSetup, CompileAnalysis }

case class CacheAwareStore(localStore: AnalysisStore, cacheProvider: CacheProvider, projectLocation: File) extends AnalysisStore {
  override def set(analysis: CompileAnalysis, setup: MiniSetup): Unit = {
    localStore.set(analysis, setup)
  }

  override def get(): Option[(CompileAnalysis, MiniSetup)] = {
    val previous = localStore.get()
    val cache = cacheProvider.findCache(previous)
    val cachedResult = cache.flatMap(_.loadCache(projectLocation))

    cachedResult.orElse(previous)
  }
}
