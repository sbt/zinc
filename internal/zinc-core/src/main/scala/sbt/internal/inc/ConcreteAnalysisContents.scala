/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc

import xsbti.compile.{ AnalysisContents, CompileAnalysis, MiniSetup }

final case class ConcreteAnalysisContents(analysis: CompileAnalysis, miniSetup: MiniSetup)
    extends AnalysisContents {
  override def getAnalysis: CompileAnalysis = analysis
  override def getMiniSetup: MiniSetup = miniSetup
}
