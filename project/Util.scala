import sbt._
import Keys._
import xsbti.compile.CompileAnalysis

object Util {
  lazy val apiDefinitions = TaskKey[Seq[File]]("api-definitions")
  lazy val genTestResTask = TaskKey[Seq[File]]("gen-test-resources")

  def lastCompilationTime(analysis0: CompileAnalysis): Long = {
    val analysis = analysis0 match { case a: sbt.internal.inc.Analysis => a }
    val lastCompilation = analysis.compilations.allCompilations.lastOption
    lastCompilation.map(_.getStartTime) getOrElse 0L
  }
  def generateVersionFile(
      version: String,
      dir: File,
      s: TaskStreams,
      analysis0: CompileAnalysis
  ): Seq[File] = {
    import java.util.{ Date, TimeZone }
    val analysis = analysis0 match { case a: sbt.internal.inc.Analysis => a }
    val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
    val timestamp = formatter.format(new Date)
    val content = versionLine(version) + "\ntimestamp=" + timestamp
    val f = dir / "incrementalcompiler.version.properties"
    // TODO: replace lastModified() with sbt.io.IO.getModifiedTimeOrZero(), once the build
    // has been upgraded to a version of sbt that includes that call.
    if (!f.exists || f.lastModified < lastCompilationTime(analysis) || !containsVersion(f, version)) {
      s.log.info("Writing version information to " + f + " :\n" + content)
      IO.write(f, content)
    }
    f :: Nil
  }
  def versionLine(version: String): String = "version=" + version
  def containsVersion(propFile: File, version: String): Boolean =
    IO.read(propFile).contains(versionLine(version))

  def sampleProjectSettings(ext: String) =
    Seq(
      (scalaSource in Compile) := baseDirectory.value / "src",
      genTestResTask := {
        def resurcesDir = (file("zinc") / "src" / "test" / "resources" / "bin").getAbsoluteFile
        val target = resurcesDir / s"${name.value}.$ext"
        IO.copyFile((packageBin in Compile).value, target)
        Seq(target)
      }
    ) ++ relaxNon212

  def relaxNon212: Seq[Setting[_]] = Seq(
    scalacOptions := {
      val old = scalacOptions.value
      scalaBinaryVersion.value match {
        case "2.12" => old
        case _ =>
          old filterNot Set(
            "-Xfatal-warnings",
            "-deprecation",
            "-Ywarn-unused",
            "-Ywarn-unused-import"
          )
      }
    }
  )
}
