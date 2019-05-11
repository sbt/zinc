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

package sbt.internal.inc

import java.io.File

import xsbti.compile.OutputGroup

final class ConcreteSingleOutput(val getOutputDirectory: File) extends xsbti.compile.SingleOutput {
  override def toString: String = s"SingleOutput($getOutputDirectory)"
}

final class ConcreteMultipleOutput(val getOutputGroups: Array[OutputGroup])
    extends xsbti.compile.MultipleOutput {
  override def toString = s"MultipleOutput($getOutputGroups)"
}
