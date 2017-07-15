/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.util.Optional

import xsbti.compile.{ AnalysisContents, AnalysisStore }

object AnalysisStore {
  def cached(backing: AnalysisStore): AnalysisStore = new CachedAnalysisStore(backing)
  private final class CachedAnalysisStore(backing: AnalysisStore) extends AnalysisStore {
    private var lastStore: Optional[AnalysisContents] = Optional.empty()
    override def get(): Optional[AnalysisContents] = {
      if (!lastStore.isPresent())
        lastStore = backing.get()
      lastStore
    }

    override def set(analysisFile: AnalysisContents): Unit = {
      backing.set(analysisFile)
      lastStore = Optional.of(analysisFile)
    }
  }

  def sync(backing: AnalysisStore): AnalysisStore = new SyncedAnalysisStore(backing)
  private final class SyncedAnalysisStore(backing: AnalysisStore) extends AnalysisStore {
    override def get(): Optional[AnalysisContents] = synchronized {
      backing.get()
    }

    override def set(analysisFile: AnalysisContents): Unit = synchronized {
      backing.set(analysisFile)
    }
  }
}
