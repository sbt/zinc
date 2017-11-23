/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import xsbti.compile.{ AnalysisContents, CompileAnalysis, MiniSetup }

final case class ConcreteAnalysisContents(analysis: CompileAnalysis, miniSetup: MiniSetup)
    extends AnalysisContents {
  override def getAnalysis: CompileAnalysis = analysis
  override def getMiniSetup: MiniSetup = miniSetup
}
