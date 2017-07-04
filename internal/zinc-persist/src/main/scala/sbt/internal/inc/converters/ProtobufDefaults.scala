package sbt.internal.inc.converters

import xsbti.api._
import sbt.internal.inc.schema

object ProtobufDefaults {
  final val MissingInt: Int = -1
  final val MissingString: String = ""

  object ReadersConstants {
    final val This: This = new This()
    final val Public: Public = new Public()
    final val EmptyType: EmptyType = new EmptyType()
    final val Unqualified: Unqualified = new Unqualified()
    final val ThisQualifier: ThisQualifier = new ThisQualifier()
  }

  object WritersConstants {
    final val This: schema.This = schema.This.defaultInstance
    final val PublicAccess: schema.Public = schema.Public.defaultInstance
    final val Unqualified: schema.Unqualified = schema.Unqualified.defaultInstance
    final val EmptyType: schema.Type.EmptyType = schema.Type.EmptyType.defaultInstance
    final val ThisQualifier: schema.ThisQualifier = schema.ThisQualifier.defaultInstance
  }

  object Classes {
    final val AnnotationClazz = classOf[schema.Annotation]
    final val ParameterizedClazz = classOf[schema.Type.Parameterized]
    final val PolymorphicClazz = classOf[schema.Type.Polymorphic]
    final val ConstantClazz = classOf[schema.Type.Constant]
    final val ExistentialClazz = classOf[schema.Type.Existential]
    final val SingletonClazz = classOf[schema.Type.Singleton]
    final val AnnotatedClazz = classOf[schema.Type.Annotated]
    final val MethodParamClazz = classOf[schema.MethodParameter]
    final val ValClazz = classOf[schema.ClassDefinition.Val]
    final val VarClazz = classOf[schema.ClassDefinition.Var]
    final val TypeAliasClazz = classOf[schema.ClassDefinition.TypeAlias]
    final val TypeDeclarationClazz = classOf[schema.ClassDefinition.TypeDeclaration]
    final val DefClazz = classOf[schema.ClassDefinition.Def]
    final val PathComponentClazz = classOf[schema.Path.PathComponent]
    final val ComponentClazz = classOf[schema.Path.PathComponent.Component]
    final val ClassLikeClazz = classOf[schema.ClassLike]
    final val ClassDefClazz = classOf[schema.ClassDefinition]
  }

  object Feedback {
    object Writers {
      final val UnexpectedEmptyOutput =
        "Expected `Output` to be either `SingleOutput` or `MultipleOutput`."
    }

    object Readers {
      final def unrecognizedSeverity(id: Int) =
        s"Unrecognized `Severity` level when reading data (id = $id)."
      final def unrecognizedUseScope(id: Int) =
        s"Unrecognized `UseScope` level when reading data (id = $id)."
      final def unrecognizedOrder(id: Int) =
        s"Unrecognized `CompileOrder` when reading data (id = $id)."
      final def missingBaseIn(clazz: Class[_]) =
        s"Missing base type in `${clazz.getName}` when reading data."
      final def missingTypeIn(clazz: Class[_]) =
        s"Missing type in `${clazz.getName}` when reading data."
      final def missingReturnTypeIn(clazz: Class[_]) =
        s"Missing return type in `${clazz.getName}` when reading data."
      final def missingLowerBoundIn(clazz: Class[_]) =
        s"Missing lower bound in `${clazz.getName}` when reading data."
      final def missingUpperBoundIn(clazz: Class[_]) =
        s"Missing upper bound in `${clazz.getName}` when reading data."
      final def missing(culprit: Class[_], owner: Class[_]) =
        s"Missing `${culprit.getName}` in `${owner.getName}` when reading data."

      final val UnexpectedEmptyType = "Unexpected empty type when reading `schema.Type`."
      final val UnexpectedEmptyClassDefinition =
        "Unexpected empty `ClassDefinition` type when reading `schema.ClassDefinition`."
      final val UnrecognizedParamModifier =
        "Unrecognized param modifier when reading `schema.MethodParameter`."
      final val UnrecognizedVariance = "Unrecognized variance when reading `schema.TypeParameter`."
      final val UnrecognizedDefinitionType =
        "Unrecognized definition type when reading `schema.ClassDefinition.ClassLikeDef`."
      final val UnexpectedAccessType = "Unexpected access type when reading `schema.Access`."
      final val UnexpectedEmptyQualifier =
        "Unexpected empty qualifier when reading `schema.Access`."
      final val MissingModifiersInDef = "Missing modifiers in `ClassDefinition` while reading."
      final val MissingAccessInDef = "Missing access in `ClassDefinition` while reading."
      final val MissingQualifierInAccess = "Missing qualifier in `Access` while reading."
      final val MissingPathInSingleton = "Missing path in `Singleton` when reading data."
      final val MissingLowerBoundInParam =
        "Missing lower bound in `TypeParameter` when reading data."
      final val MissingUpperBoundInParam =
        "Missing upper bound in `TypeParameter` when reading data."
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
