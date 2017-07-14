/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File

case class SimpleOutputGroup(getSourceDirectory: File, getOutputDirectory: File)
    extends xsbti.compile.OutputGroup {
  override def toString = s"OutputGroup($getSourceDirectory -> $getOutputDirectory)"
}
