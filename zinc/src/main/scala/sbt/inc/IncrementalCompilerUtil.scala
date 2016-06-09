package sbt
package inc

import java.io.File
import sbt.util.Logger
import xsbti.Maybe
import xsbti.compile.{ IncrementalCompiler, FileWatch }
import sbt.internal.inc.{ IncrementalCompilerImpl, NoFileWatch, BarbaryFileWatch }

object IncrementalCompilerUtil {
  def defaultIncrementalCompiler: IncrementalCompiler =
    new IncrementalCompilerImpl

  val noFileWatch: FileWatch = NoFileWatch
  def barbaryFileWatch(sourcesToWatch: List[File], log: Logger): FileWatch =
    new BarbaryFileWatch(sourcesToWatch, log)
}
