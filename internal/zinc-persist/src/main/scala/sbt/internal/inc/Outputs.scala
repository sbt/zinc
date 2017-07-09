/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File

import xsbti.compile.OutputGroup

class ConcreteSingleOutput(val getOutputDirectory: File) extends xsbti.compile.SingleOutput {
  override def toString: String = s"SingleOutput($getOutputDirectory)"
}

class ConcreteMultipleOutput(val getOutputGroups: Array[OutputGroup])
    extends xsbti.compile.MultipleOutput {
  override def toString = s"MultipleOutput($getOutputGroups)"
}
