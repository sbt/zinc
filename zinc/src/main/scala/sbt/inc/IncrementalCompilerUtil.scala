/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package inc

import xsbti.compile.IncrementalCompiler
import sbt.internal.inc.IncrementalCompilerImpl

object IncrementalCompilerUtil {
  def defaultIncrementalCompiler: IncrementalCompiler =
    new IncrementalCompilerImpl
}
