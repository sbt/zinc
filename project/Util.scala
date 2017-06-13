import sbt._
import Keys._

import sbt.internal.inc.Analysis

object Util {
  lazy val apiDefinitions = TaskKey[Seq[File]]("api-definitions")

  def lastCompilationTime(analysis: Analysis): Long = {
    val lastCompilation = analysis.compilations.allCompilations.lastOption
    lastCompilation.map(_.getStartTime) getOrElse 0L
  }
  def generateVersionFile(version: String,
                          dir: File,
                          s: TaskStreams,
                          analysis: Analysis): Seq[File] = {
    import java.util.{ Date, TimeZone }
    val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
    val timestamp = formatter.format(new Date)
    val content = versionLine(version) + "\ntimestamp=" + timestamp
    val f = dir / "incrementalcompiler.version.properties"
    if (!f.exists || f.lastModified < lastCompilationTime(analysis) || !containsVersion(f,
                                                                                        version)) {
      s.log.info("Writing version information to " + f + " :\n" + content)
      IO.write(f, content)
    }
    f :: Nil
  }
  def versionLine(version: String): String = "version=" + version
  def containsVersion(propFile: File, version: String): Boolean =
    IO.read(propFile).contains(versionLine(version))
}
