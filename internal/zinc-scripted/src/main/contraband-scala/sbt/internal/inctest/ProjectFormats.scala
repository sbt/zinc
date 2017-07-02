/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.inctest
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ProjectFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ProjectFormat: JsonFormat[sbt.internal.inctest.Project] = new JsonFormat[sbt.internal.inctest.Project] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.inctest.Project = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val dependsOn = unbuilder.readField[Vector[String]]("dependsOn")
      val in = unbuilder.readField[Option[java.io.File]]("in")
      val scalaVersion = unbuilder.readField[Option[String]]("scalaVersion")
      unbuilder.endObject()
      sbt.internal.inctest.Project(name, dependsOn, in, scalaVersion)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.inctest.Project, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("dependsOn", obj.dependsOn)
    builder.addField("in", obj.in)
    builder.addField("scalaVersion", obj.scalaVersion)
    builder.endObject()
  }
}
}
