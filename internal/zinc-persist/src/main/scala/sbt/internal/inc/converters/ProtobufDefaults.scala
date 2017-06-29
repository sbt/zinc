package sbt.internal.inc.converters

import sbt.internal.inc.schema
import xsbti.api.{
  Annotated,
  Annotation,
  Constant,
  EmptyType,
  Existential,
  Parameterized,
  Polymorphic,
  Singleton,
  This
}

object ProtobufDefaults {
  final val MissingInt: Int = -1
  final val MissingString: String = ""

  object ReadersConstants {
    final val EmptyType: EmptyType = new EmptyType()
    final val This: This = new This()
  }

  object WritersConstants {
    final val This: schema.This = schema.This.defaultInstance
    final val PublicAccess: schema.Public = schema.Public.defaultInstance
    final val Unqualified: schema.Unqualified = schema.Unqualified.defaultInstance
    final val EmptyType: schema.Type.EmptyType = schema.Type.EmptyType.defaultInstance
    final val ThisQualifier: schema.ThisQualifier = schema.ThisQualifier.defaultInstance
  }

  object Classes {
    final val AnnotationClazz = classOf[Annotation]
    final val ParameterizedClazz = classOf[Parameterized]
    final val PolymorphicClazz = classOf[Polymorphic]
    final val ConstantClazz = classOf[Constant]
    final val ExistentialClazz = classOf[Existential]
    final val SingletonClazz = classOf[Singleton]
    final val AnnotatedClazz = classOf[Annotated]
  }

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
      final def missingBaseIn(clazz: Class[_]) =
        s"Missing base type in `${clazz.getName}` when reading data."

      final val MissingPathInSingleton = "Missing path in `Singleton` when reading data."
      final val MissingPrefixInProjection = "Missing prefix in `Projection` when reading data."
      final val MissingPathInSuper =
        "Missing qualifier of type `Path` in `Super` when reading data."
      final val MissingMiniOptions = "`MiniOptions` are missing in `MiniSetup` when reading data."
      final val ExpectedNonEmptyOutput = "Expected non-empty `Output` when reading data."
      final val ExpectedPositionInProblem: String =
        "Expected non-empty `Position` in `Problem` when reading data."
    }
  }
}
