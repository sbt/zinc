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

package xsbti

import xsbti.compile.analysis.SourceInfo

class TestSourceInfo extends SourceInfo {

  override def getReportedProblems: Array[Problem] = Array.empty[Problem]

  override def getUnreportedProblems: Array[Problem] = Array.empty[Problem]

  override def getMainClasses: Array[String] = Array.empty[String]
}
