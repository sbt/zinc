import sbt._
import Keys._

object ZincBuildUtil {
  lazy val apiDefinitions = TaskKey[Seq[File]]("api-definitions")
  lazy val genTestResTask = TaskKey[Seq[File]]("gen-test-resources")

  def sampleProjectSettings(ext: String) =
    Seq(
      (Compile / scalaSource) := baseDirectory.value / "src",
      genTestResTask := {
        def resurcesDir = (file("zinc") / "src" / "test" / "resources" / "bin").getAbsoluteFile
        val target = resurcesDir / s"${name.value}.$ext"
        IO.copyFile((Compile / packageBin).value, target)
        Seq(target)
      }
    ) ++ relaxNon212

  def relaxNon212: Seq[Setting[?]] = Seq(
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

  import com.typesafe.tools.mima.core._
  import com.typesafe.tools.mima.core.ProblemFilters._
  def excludeInternalProblems = {
    Seq(
      exclude[DirectMissingMethodProblem]("sbt.internal.*"),
      exclude[IncompatibleSignatureProblem]("sbt.internal.*"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.*"),
      exclude[ReversedMissingMethodProblem]("sbt.internal.*"),
      exclude[MissingClassProblem]("sbt.internal.*"),
      exclude[IncompatibleResultTypeProblem]("sbt.internal.*"),
      exclude[MissingTypesProblem]("sbt.internal.*"),
      exclude[InheritedNewAbstractMethodProblem](
        "sbt.internal.*"
      ),
      exclude[FinalClassProblem]("sbt.internal.*"),
      exclude[DirectAbstractMethodProblem]("sbt.internal.*"),
    )
  }
}
