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

package sbt
package internal
package inc

import xsbti.compile.{ MultipleOutput, Output, OutputGroup, SingleOutput }
import java.io.File

/**
 * Define helpers to create [[CompileOutput]] to pass to the incremental
 * compiler. Both `SingleOutput` and `MultipleOutput` are supported.
 */
object CompileOutput {

  /**
   * Create a `SingleOutput`.
   *
   * @param dir The directory where you want the compiler to store class files.
   * @return An instance of `SingleOutput` that stores contents in <code>dir</code>.
   */
  def apply(dir: File): Output = new SingleOutput {
    def getOutputDirectory = dir
    override def toString = s"SingleOutput($getOutputDirectory)"
  }

  /**
   * Create a `MultipleOutput`. This method is useful when you want to
   * compile several sources and store them in different output directories.
   *
   * @param groups A collection of tuples mapping from a source dir to an output dir.
   * @return An instance of `MultipleOutput`.
   */
  def apply(groups: (File, File)*): Output = new MultipleOutput {
    def getOutputGroups = groups.toArray map {
      case (src, out) =>
        new OutputGroup {
          def getSourceDirectory = src
          def getOutputDirectory = out
          override def toString = s"OutputGroup($src -> $out)"
        }
    }
    override def toString = s"MultiOutput($getOutputGroups)"
  }
}
