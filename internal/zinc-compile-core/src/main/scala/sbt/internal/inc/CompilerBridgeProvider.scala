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
import xsbti.compile.CompilerBridgeProvider

object CompilerBridgeProvider {
  def constant(file: File): CompilerBridgeProvider = new CompilerBridgeProvider {
    override def getBridgeSources(scalaInstance: xsbti.compile.ScalaInstance,
                                  log: xsbti.Logger): File = file
  }
}
