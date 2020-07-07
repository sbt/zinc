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

import xsbti.compile.{ Output, OutputGroup }
import java.nio.file.Path
import java.util.Optional

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
  def apply(dir: Path): Output = new ConcreteSingleOutput(dir)

  /**
   * Create a `MultipleOutput`. This method is useful when you want to
   * compile several sources and store them in different output directories.
   *
   * @param groups A collection of tuples mapping from a source dir to an output dir.
   * @return An instance of `MultipleOutput`.
   */
  def apply(groups: (Path, Path)*): Output = {
    val gs = groups.toArray map {
      case (src, out) => outputGroup(src, out)
    }
    apply(gs)
  }

  def apply(groups: Array[OutputGroup]): Output = new ConcreteMultipleOutput(groups)

  lazy val empty: Output = new EmptyOutput()

  def outputGroup(source: Path, output: Path): OutputGroup =
    new ConcreteOutputGroup(source, output)

  private final class EmptyOutput extends xsbti.compile.Output {
    override def getSingleOutput(): Optional[Path] = Optional.empty()
    override def getMultipleOutput(): Optional[Array[OutputGroup]] = Optional.empty()
    override def toString: String = "EmptyOutput()"
  }

  private final class ConcreteSingleOutput(val getOutputDirectory: Path)
      extends xsbti.compile.SingleOutput {
    override def toString: String = s"SingleOutput($getOutputDirectory)"
  }

  private final class ConcreteMultipleOutput(val getOutputGroups: Array[OutputGroup])
      extends xsbti.compile.MultipleOutput {
    override def toString = s"MultipleOutput($getOutputGroups)"
  }

  private final class ConcreteOutputGroup(
      val getSourceDirectory: Path,
      val getOutputDirectory: Path
  ) extends xsbti.compile.OutputGroup {
    override def toString = s"OutputGroup($getSourceDirectory -> $getOutputDirectory)"
  }
}
