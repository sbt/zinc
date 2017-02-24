/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File
import sbt.util.Logger

trait CompilerBridgeProvider {
  def apply(scalaInstance: xsbti.compile.ScalaInstance, log: Logger): File
}

object CompilerBridgeProvider {
  def constant(file: File): CompilerBridgeProvider = new CompilerBridgeProvider {
    def apply(scalaInstance: xsbti.compile.ScalaInstance, log: Logger): File = file
  }
}
