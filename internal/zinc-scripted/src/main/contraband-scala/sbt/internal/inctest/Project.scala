/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.inctest
final class Project private (
  val name: String,
  val dependsOn: Vector[String],
  val in: Option[java.io.File]) extends Serializable {
  
  private def this(name: String) = this(name, Vector(), None)
  
  override def equals(o: Any): Boolean = o match {
    case x: Project => (this.name == x.name) && (this.dependsOn == x.dependsOn) && (this.in == x.in)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + name.##) + dependsOn.##) + in.##)
  }
  override def toString: String = {
    "Project(" + name + ", " + dependsOn + ", " + in + ")"
  }
  protected[this] def copy(name: String = name, dependsOn: Vector[String] = dependsOn, in: Option[java.io.File] = in): Project = {
    new Project(name, dependsOn, in)
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
}
object Project {
  
  def apply(name: String): Project = new Project(name, Vector(), None)
  def apply(name: String, dependsOn: Vector[String], in: Option[java.io.File]): Project = new Project(name, dependsOn, in)
  def apply(name: String, dependsOn: Vector[String], in: java.io.File): Project = new Project(name, dependsOn, Option(in))
}
