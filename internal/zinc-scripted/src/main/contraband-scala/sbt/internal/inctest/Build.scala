/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.inctest
final class Build private (
  val projects: Vector[sbt.internal.inctest.Project]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Build => (this.projects == x.projects)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.inctest.Build".##) + projects.##)
  }
  override def toString: String = {
    "Build(" + projects + ")"
  }
  protected[this] def copy(projects: Vector[sbt.internal.inctest.Project] = projects): Build = {
    new Build(projects)
  }
  def withProjects(projects: Vector[sbt.internal.inctest.Project]): Build = {
    copy(projects = projects)
  }
}
object Build {
  
  def apply(projects: Vector[sbt.internal.inctest.Project]): Build = new Build(projects)
}
