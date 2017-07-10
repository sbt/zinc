/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.cached

import java.io.File
import java.util.Optional

import sbt.internal.inc.ConcreteAnalysisContents
import xsbti.compile.{ AnalysisContents, AnalysisStore }

case class CacheAwareStore(localStore: AnalysisStore,
                           cacheProvider: CacheProvider,
                           projectLocation: File)
    extends AnalysisStore {
  override def set(analysisFile: AnalysisContents): Unit =
    localStore.set(analysisFile)

  final val Empty = Optional.empty[AnalysisContents]
  override def get: Optional[AnalysisContents] = {
    import sbt.internal.inc.JavaInterfaceUtil.EnrichOptional
    val previous = localStore.get().toOption.map(f => (f.getAnalysis, f.getMiniSetup))
    val cache = cacheProvider.findCache(previous)
    val cachedResult = cache.flatMap(_.loadCache(projectLocation))
    val optResult = cachedResult.orElse(previous)
    optResult match {
      case Some((analysis, setup)) => Optional.of(ConcreteAnalysisContents(analysis, setup))
      case None                    => Empty
    }
  }
}
