package sbt
package inc

import java.io.File
import sbt.util.Logger
import scala.util.Properties
import java.util.Locale
import xsbti.Maybe
import xsbti.compile.{ IncrementalCompiler, FileWatch }
import sbt.internal.inc.{ IncrementalCompilerImpl, NoFileWatch, BarbaryFileWatch, JDK7FileWatch }

object IncrementalCompilerUtil {
  def defaultIncrementalCompiler: IncrementalCompiler =
    new IncrementalCompilerImpl

  val noFileWatch: FileWatch = NoFileWatch
  def fileWatch(sourcesToWatch: List[File], log: Logger): FileWatch =
    os match {
      case (Windows | Linux) if Properties.isJavaAtLeast("1.7") => new JDK7FileWatch(sourcesToWatch, log)
      case OSX if Properties.isJavaAtLeast("1.7") => new BarbaryFileWatch(sourcesToWatch, log)
      case _ => noFileWatch
    }

  private sealed trait OS
  private case object Windows extends OS
  private case object Linux extends OS
  private case object OSX extends OS
  private case object Other extends OS

  private val os: OS = {
    sys.props.get("os.name").map { name =>
      name.toLowerCase(Locale.ENGLISH) match {
        case osx if osx.contains("darwin") || osx.contains("mac") => OSX
        case windows if windows.contains("windows") => Windows
        case linux if linux.contains("linux") => Linux
        case _ => Other
      }
    }.getOrElse(Other)
  }
}
