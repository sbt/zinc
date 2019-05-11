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

import java.io.File
import java.util.Optional

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
      case Some((analysis, setup)) => Optional.of(AnalysisContents.create(analysis, setup))
      case None                    => Empty
    }
  }
}
