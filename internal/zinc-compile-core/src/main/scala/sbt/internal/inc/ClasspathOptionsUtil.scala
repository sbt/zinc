/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
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