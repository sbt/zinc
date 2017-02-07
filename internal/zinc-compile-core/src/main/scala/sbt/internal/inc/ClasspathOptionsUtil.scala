/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbti.compile.ClasspathOptions

object ClasspathOptionsUtil {
  def manual = new ClasspathOptions(false, false, false, true, false)
  def boot = new ClasspathOptions(true, false, false, true, true)
  def repl = auto
  def javac(compiler: Boolean) = new ClasspathOptions(false, compiler, false, false, false)
  def auto = new ClasspathOptions(true, true, true, true, true)
}