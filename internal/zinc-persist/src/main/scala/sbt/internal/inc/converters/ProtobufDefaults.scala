/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

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
    final val Annotation = classOf[schema.Annotation]
    final val Parameterized = classOf[schema.Type.Parameterized]
    final val ParamModifier = classOf[schema.ParameterModifier]
    final val Polymorphic = classOf[schema.Type.Polymorphic]
    final val Constant = classOf[schema.Type.Constant]
    final val Existential = classOf[schema.Type.Existential]
    final val Singleton = classOf[schema.Type.Singleton]
    final val Annotated = classOf[schema.Type.Annotated]
    final val MethodParameter = classOf[schema.MethodParameter]
    final val Val = classOf[schema.ClassDefinition.Val]
    final val Var = classOf[schema.ClassDefinition.Var]
    final val TypeAlias = classOf[schema.ClassDefinition.TypeAlias]
    final val TypeDeclaration = classOf[schema.ClassDefinition.TypeDeclaration]
    final val Def = classOf[schema.ClassDefinition.Def]
    final val PathComponent = classOf[schema.Path.PathComponent]
    final val Component = classOf[schema.Path.PathComponent.Component]
    final val ClassLike = classOf[schema.ClassLike]
    final val Structure = classOf[schema.Type.Structure]
    final val ClassLikeDef = classOf[schema.ClassDefinition.ClassLikeDef]
    final val ClassDefinition = classOf[schema.ClassDefinition]
    final val TypeParameter = classOf[schema.TypeParameter]
    final val Type = classOf[schema.Type]
    final val Projection = classOf[schema.Type.Projection]
    final val Access = classOf[schema.Access]
    final val Modifiers = classOf[schema.Modifiers]
    final val Severity = classOf[schema.Severity]
    final val UseScope = classOf[schema.UseScope]
    final val CompileOrder = classOf[schema.CompileOrder]
    final val Path = classOf[schema.Path]
    final val Super = classOf[schema.Super]
    final val MiniOptions = classOf[schema.MiniOptions]
    final val MiniSetup = classOf[schema.MiniSetup]
    final val CompilationOutput = classOf[schema.Compilation.Output]
    final val MiniSetupOutput = classOf[schema.MiniSetup.Output]
    final val Position = classOf[schema.Position]
    final val Problem = classOf[schema.Problem]
    final val Companions = classOf[schema.Companions]
    final val Relations = classOf[schema.Relations]
    final val Stamps = classOf[schema.Stamps]
    final val Compilations = classOf[schema.Compilations]
    final val SourceInfos = classOf[schema.SourceInfos]
    final val AnalyzedClass = classOf[schema.AnalyzedClass]
    final val Analysis = classOf[schema.Analysis]
    final val APIs = classOf[schema.APIs]
    final val APIsFile = classOf[schema.APIsFile]
  }

  object Feedback {
    implicit class StringToException(str: String) {
      def !!(): Nothing = sys.error(str)
    }

    object Writers {
      final val UnexpectedEmptyOutput =
        "Expected `Output` to be either `SingleOutput` or `MultipleOutput`."
    }

    object Readers {
      final val ReadError: String = "Protobuf read error"

      final def expectedBaseIn(clazz: Class[_]) =
        expected("base type", clazz)
      final def expectedTypeIn(clazz: Class[_]) =
        expected("type", clazz)
      final val ExpectedReturnTypeInDef =
        expected("return type", Classes.Def)
      final val ExpectedUpperBoundInTypeParameter =
        expected("upper bound", Classes.TypeParameter)
      final val ExpectedUpperBoundInTypeDeclaration =
        expected("upper bound", Classes.TypeDeclaration)
      final val ExpectedLowerBoundInTypeParameter =
        expected("lower bound", Classes.TypeParameter)
      final val ExpectedLowerBoundInTypeDeclaration =
        expected("lower bound", Classes.TypeDeclaration)
      final val ExpectedNonEmptyType =
        expected(s"non empty type", Classes.Type)
      final val ExpectedNonEmptyDefType =
        expected(s"non empty `${Classes.ClassDefinition.getName}` type", Classes.Type)
      final val ExpectedPathInSingleton =
        expected("path", Classes.Singleton)
      final val ExpectedPrefixInProjection =
        expected("prefix", Classes.Projection)
      final val ExpectedQualifierInAccess =
        expected("qualifier", Classes.Access)
      final val MissingModifiersInDef =
        expected("modifiers", Classes.ClassDefinition)
      final val MissingAccessInDef =
        expected("access", Classes.ClassDefinition)
      final val ExpectedValidAccessType =
        expected("valid access type", Classes.Access)
      final val ExpectedAccessInClassLike =
        expected(Classes.Access, Classes.ClassLike)
      final val ExpectedNonEmptyQualifier =
        expected("non-empty qualifier", Classes.Access)
      final val ExpectedCompanionsInAnalyzedClass =
        expected(Classes.Companions, Classes.AnalyzedClass)
      final val ExpectedPathInSuper =
        expected(s"qualifier of type ${Classes.Path}", Classes.Super)
      final val ExpectedMiniOptionsInSetup =
        expected(Classes.MiniOptions, Classes.MiniSetup)
      final val ExpectedOutputInCompilationOutput =
        expected("non-empty output", Classes.CompilationOutput)
      final val ExpectedOutputInMiniSetupOutput =
        expected("non-empty output", Classes.MiniSetupOutput)
      final val ExpectedPositionInProblem: String =
        expected(Classes.Position, Classes.Problem)
      final val ExpectedApisInApisFile: String =
        expected(Classes.APIs, Classes.APIsFile)

      final def expected(culprit: Class[_], owner: Class[_]): String =
        expected(s"`${culprit.getName}`", owner)
      final def expected(something: String, owner: Class[_]): String =
        s"$ReadError: Expected $something in `${owner.getName}`."

      final val UnrecognizedParamModifier =
        unrecognized("param modifier", Classes.MethodParameter)
      final val UnrecognizedVariance =
        unrecognized("variance", Classes.TypeParameter)
      final val UnrecognizedDefinitionType =
        unrecognized("definition type", Classes.ClassLikeDef)
      final def unrecognizedSeverity(id: Int) =
        unrecognized(Classes.Severity, id.toString)
      final def unrecognizedUseScope(id: Int) =
        unrecognized(Classes.UseScope, id.toString)
      final def unrecognizedOrder(id: Int) =
        unrecognized(Classes.CompileOrder, id.toString)

      final def unrecognized[T](culprit: Class[_], value: String): String =
        s"$ReadError: Unrecognized ${culprit.getName} with value `$value`."
      final def unrecognized(culprit: Class[_], owner: Class[_]): String =
        unrecognized(s"`${culprit.getName}`", owner)
      final def unrecognized(something: String, owner: Class[_]): String =
        s"$ReadError: Unrecognized $something in `${owner.getName}`."
    }
  }
}
