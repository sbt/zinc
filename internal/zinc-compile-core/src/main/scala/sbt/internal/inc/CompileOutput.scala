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

/**
 * Define helpers to create [[CompileOutput]] to pass to the incremental
 * compiler. Both [[SingleOutput]] and [[MultipleOutput]] are supported.
 */
object CompileOutput {

  /**
   * Create a [[SingleOutput]].
   *
   * @param dir The directory where you want the compiler to store class files.
   * @return An instance of [[SingleOutput]] that stores contents in <code>dir</code>.
   */
  def apply(dir: File): Output = new SingleOutput {
    def outputDirectory = dir
    override def toString = s"SingleOutput($outputDirectory)"
  }

  /**
   * Create a [[MultipleOutput]]. This method is useful when you want to
   * compile several sources and store them in different output directories.
   *
   * @param groups A collection of tuples mapping from a source dir to an output dir.
   * @return An instance of [[MultipleOutput]].
   */
  def apply(groups: (File, File)*): Output = new MultipleOutput {
    def outputGroups = groups.toArray map {
      case (src, out) =>
        new MultipleOutput.OutputGroup {
          def sourceDirectory = src
          def outputDirectory = out
          override def toString = s"OutputGroup($src -> $out)"
        }
    }
    override def toString = s"MultiOutput($outputGroups)"
  }
}
