/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbti.compile.{ CompileAnalysis, MiniSetup }

trait AnalysisStore {
  def set(analysis: CompileAnalysis, setup: MiniSetup): Unit
  def get(): Option[(CompileAnalysis, MiniSetup)]
}

object AnalysisStore {
  def cached(backing: AnalysisStore): AnalysisStore = new AnalysisStore {
    private var last: Option[(CompileAnalysis, MiniSetup)] = None
    def set(analysis: CompileAnalysis, setup: MiniSetup): Unit = {
      backing.set(analysis, setup)
      last = Some((analysis, setup))
    }
    def get(): Option[(CompileAnalysis, MiniSetup)] =
      {
        if (last.isEmpty)
          last = backing.get()
        last
      }
  }
  def sync(backing: AnalysisStore): AnalysisStore = new AnalysisStore {
    def set(analysis: CompileAnalysis, setup: MiniSetup): Unit = synchronized { backing.set(analysis, setup) }
    def get(): Option[(CompileAnalysis, MiniSetup)] = synchronized { backing.get() }
  }
}
