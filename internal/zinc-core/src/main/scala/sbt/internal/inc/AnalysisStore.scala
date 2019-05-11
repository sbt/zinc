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

package sbt
package internal
package inc

import java.util.Optional

import xsbti.compile.{ AnalysisContents, AnalysisStore => XAnalysisStore }

object AnalysisStore {
  def cached(backing: XAnalysisStore): XAnalysisStore = new CachedAnalysisStore(backing)
  private final class CachedAnalysisStore(backing: XAnalysisStore) extends XAnalysisStore {
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

  def sync(backing: XAnalysisStore): XAnalysisStore = new SyncedAnalysisStore(backing)
  private final class SyncedAnalysisStore(backing: XAnalysisStore) extends XAnalysisStore {
    override def get(): Optional[AnalysisContents] = synchronized {
      backing.get()
    }

    override def set(analysisFile: AnalysisContents): Unit = synchronized {
      backing.set(analysisFile)
    }
  }
}
