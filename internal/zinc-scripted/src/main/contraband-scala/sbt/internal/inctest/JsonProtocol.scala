/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.inctest
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.inctest.ProjectFormats
  with sbt.internal.inctest.BuildFormats
object JsonProtocol extends JsonProtocol