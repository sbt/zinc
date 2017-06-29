package sbt.internal.inc.converters

import sbt.internal.inc.schema
import xsbti.api.This

object ProtobufDefaults {
  final val MissingInt: Int = -1
  final val MissingString: String = ""

  final val This: This = new This()
  final val ThisQualifier: schema.ThisQualifier = schema.ThisQualifier.defaultInstance
  final val Unqualified: schema.Unqualified = schema.Unqualified.defaultInstance
  final val PublicAccess: schema.Public = schema.Public.defaultInstance

  object Feedback {
    object Writers {
      final val ExpectedNonEmptyOutput =
        "Expected `Output` to be either `SingleOutput` or `MultipleOutput`."
    }

    object Readers {
      final def unrecognizedSeverity(id: Int) =
        s"Unrecognized `Severity` level when reading data (id = $id)."
      final def unrecognizedOrder(id: Int) =
        s"Unrecognized `CompileOrder` when reading data (id = $id)."
      final val MissingPathInSuper = "Missing qualifier of tyep `Path` in `Super`."
      final val MissingMiniOptions = "`MiniOptions` are missing in `MiniSetup` when reading data."
      final val ExpectedNonEmptyOutput = "Expected non-empty `Output` when reading data."
      final val ExpectedPositionInProblem: String =
        "Expected non-empty `Position` in `Problem` when reading data."
    }
  }
}
