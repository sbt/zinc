/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbti.compile.{ Output, SingleOutput, MultipleOutput }
import java.io.File

/** Constructor for the `Output` ADT for incremental compiler.  Can either take groups (src -> out) or a single output. */
object CompileOutput {
  def apply(dir: File): Output = new SingleOutput {
    def outputDirectory = dir
    override def toString = s"SingleOutput($outputDirectory)"
  }

  def apply(groups: (File, File)*): Output = new MultipleOutput {
    def outputGroups = groups.toArray map {
      case (src, out) => new MultipleOutput.OutputGroup {
        def sourceDirectory = src
        def outputDirectory = out
        override def toString = s"OutputGroup($src -> $out)"
      }
    }
    override def toString = s"MultiOutput($outputGroups)"
  }
}
