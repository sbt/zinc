import sbt.Keys.scalaVersion
import sbt.Project.{ projectToLocalProject, projectToRef }
import sbt.{ Keys, LocalProject, Project, ProjectRef }
import scala.language.experimental.macros

object ProjectMatrixShims {

  implicit def RichProject(p: Project) = new RichProject(p)

  class RichProject(p: Project) {
    def projectRefs: List[LocalProject] = projectToLocalProject(p) :: Nil
    def jvmPlatform(scalaVersions: Seq[String]) = p.settings(scalaVersion := scalaVersions.head)

    def jvmPlatform(autoScalaLibrary: Boolean) =
      p.settings(Keys.autoScalaLibrary := autoScalaLibrary)

    def jvm(version: String) = p
    def jvm(version: Boolean) = p
  }

  def projectMatrix: Project = macro sbt.Project.projectMacroImpl

}
