package sbt.internal.inc.converters

import sbt.internal.inc.schema

object ProtobufDefaults {
  final val ThisQualifier = schema.ThisQualifier.defaultInstance
  final val Unqualified = schema.Unqualified.defaultInstance
  final val PublicAccess = schema.Public.defaultInstance
}
