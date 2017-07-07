/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.inctest
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildFormats { self: sbt.internal.inctest.ProjectFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val BuildFormat: JsonFormat[sbt.internal.inctest.Build] = new JsonFormat[sbt.internal.inctest.Build] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.inctest.Build = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val projects = unbuilder.readField[Vector[sbt.internal.inctest.Project]]("projects")
      unbuilder.endObject()
      sbt.internal.inctest.Build(projects)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.inctest.Build, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("projects", obj.projects)
    builder.endObject()
  }
}
}
