package sbt.internal.inc

import xsbti.compile.{ AnalysisContents, CompileAnalysis, MiniSetup }

final case class ConcreteAnalysisContents(analysis: CompileAnalysis, miniSetup: MiniSetup)
    extends AnalysisContents {
  override def getAnalysis: CompileAnalysis = analysis
  override def getMiniSetup: MiniSetup = miniSetup
}
