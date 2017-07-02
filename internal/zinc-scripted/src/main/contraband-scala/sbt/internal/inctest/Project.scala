/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.inctest
final class Project private (
  val name: String,
  val dependsOn: Vector[String],
  val in: Option[java.io.File],
  val scalaVersion: Option[String]) extends Serializable {
  
  private def this(name: String) = this(name, Vector(), None, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: Project => (this.name == x.name) && (this.dependsOn == x.dependsOn) && (this.in == x.in) && (this.scalaVersion == x.scalaVersion)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "Project".##) + name.##) + dependsOn.##) + in.##) + scalaVersion.##)
  }
  override def toString: String = {
    "Project(" + name + ", " + dependsOn + ", " + in + ", " + scalaVersion + ")"
  }
  protected[this] def copy(name: String = name, dependsOn: Vector[String] = dependsOn, in: Option[java.io.File] = in, scalaVersion: Option[String] = scalaVersion): Project = {
    new Project(name, dependsOn, in, scalaVersion)
  }
  def withName(name: String): Project = {
    copy(name = name)
  }
  def withDependsOn(dependsOn: Vector[String]): Project = {
    copy(dependsOn = dependsOn)
  }
  def withIn(in: Option[java.io.File]): Project = {
    copy(in = in)
  }
  def withIn(in: java.io.File): Project = {
    copy(in = Option(in))
  }
  def withScalaVersion(scalaVersion: Option[String]): Project = {
    copy(scalaVersion = scalaVersion)
  }
  def withScalaVersion(scalaVersion: String): Project = {
    copy(scalaVersion = Option(scalaVersion))
  }
}
object Project {
  
  def apply(name: String): Project = new Project(name, Vector(), None, None)
  def apply(name: String, dependsOn: Vector[String], in: Option[java.io.File], scalaVersion: Option[String]): Project = new Project(name, dependsOn, in, scalaVersion)
  def apply(name: String, dependsOn: Vector[String], in: java.io.File, scalaVersion: String): Project = new Project(name, dependsOn, Option(in), Option(scalaVersion))
}
