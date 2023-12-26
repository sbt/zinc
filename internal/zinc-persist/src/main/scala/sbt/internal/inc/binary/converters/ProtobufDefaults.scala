/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc.binary.converters

import xsbti.api._
import sbt.internal.inc.Schema

object ProtobufDefaults {
  final val MissingInt: Int = -1
  final val MissingString: String = ""

  object ReadersConstants {
    final val This: This = xsbti.api.This.of()
    final val Public: Public = xsbti.api.Public.of()
    final val EmptyType: EmptyType = xsbti.api.EmptyType.of()
    final val Unqualified: Unqualified = xsbti.api.Unqualified.of()
    final val ThisQualifier: ThisQualifier = xsbti.api.ThisQualifier.of()
  }

  object WritersConstants {
    final val This: Schema.This = Schema.This.getDefaultInstance
    final val PublicAccess: Schema.Public = Schema.Public.getDefaultInstance
    final val Unqualified: Schema.Unqualified = Schema.Unqualified.getDefaultInstance
    final val EmptyType: Schema.Type.EmptyType = Schema.Type.EmptyType.getDefaultInstance
    final val ThisQualifier: Schema.ThisQualifier = Schema.ThisQualifier.getDefaultInstance
  }

  object Classes {
    final val Annotation = classOf[Schema.Annotation]
    final val Parameterized = classOf[Schema.Type.Parameterized]
    final val ParamModifier = classOf[Schema.ParameterModifier]
    final val Polymorphic = classOf[Schema.Type.Polymorphic]
    final val Constant = classOf[Schema.Type.Constant]
    final val Existential = classOf[Schema.Type.Existential]
    final val Singleton = classOf[Schema.Type.Singleton]
    final val Annotated = classOf[Schema.Type.Annotated]
    final val MethodParameter = classOf[Schema.MethodParameter]
    final val Val = classOf[Schema.ClassDefinition.Val]
    final val Var = classOf[Schema.ClassDefinition.Var]
    final val TypeAlias = classOf[Schema.ClassDefinition.TypeAlias]
    final val TypeDeclaration = classOf[Schema.ClassDefinition.TypeDeclaration]
    final val Def = classOf[Schema.ClassDefinition.Def]
    final val PathComponent = classOf[Schema.Path.PathComponent]
    final val Component = classOf[Schema.Path.PathComponent.ComponentCase]
    final val ClassLike = classOf[Schema.ClassLike]
    final val Structure = classOf[Schema.Type.Structure]
    final val ClassLikeDef = classOf[Schema.ClassDefinition.ClassLikeDef]
    final val ClassDefinition = classOf[Schema.ClassDefinition]
    final val TypeParameter = classOf[Schema.TypeParameter]
    final val Type = classOf[Schema.Type]
    final val Projection = classOf[Schema.Type.Projection]
    final val Access = classOf[Schema.Access]
    final val Modifiers = classOf[Schema.Modifiers]
    final val Severity = classOf[Schema.Severity]
    final val UseScope = classOf[Schema.UseScope]
    final val CompileOrder = classOf[Schema.CompileOrder]
    final val Path = classOf[Schema.Path]
    final val Super = classOf[Schema.Super]
    final val MiniOptions = classOf[Schema.MiniOptions]
    final val MiniSetup = classOf[Schema.MiniSetup]
    final val CompilationOutput = classOf[Schema.Compilation.OutputCase]
    final val MiniSetupOutput = classOf[Schema.MiniSetup.OutputCase]
    final val Position = classOf[Schema.Position]
    final val Problem = classOf[Schema.Problem]
    final val Companions = classOf[Schema.Companions]
    final val Relations = classOf[Schema.Relations]
    final val Stamps = classOf[Schema.Stamps]
    final val Compilations = classOf[Schema.Compilations]
    final val SourceInfos = classOf[Schema.SourceInfos]
    final val AnalyzedClass = classOf[Schema.AnalyzedClass]
    final val Analysis = classOf[Schema.Analysis]
    final val APIs = classOf[Schema.APIs]
    final val APIsFile = classOf[Schema.APIsFile]
  }

  object EmptyLazyCompanions extends Lazy[Companions] {
    override def get(): Companions =
      throw new IllegalArgumentException("No companions was stored!")
  }

  object Feedback {
    implicit class StringToException(str: String) {
      def `!!` : Nothing = sys.error(str)
    }

    object Writers {
      final val UnexpectedEmptyOutput =
        "Expected `Output` to be either `SingleOutput` or `MultipleOutput`."
    }

    object Readers {
      final val ReadError: String = "Protobuf read error"

      final def expectedBaseIn(clazz: Class[?]) =
        expected("base type", clazz)
      final def expectedTypeIn(clazz: Class[?]) =
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

      final def expected(culprit: Class[?], owner: Class[?]): String =
        expected(s"`${culprit.getName}`", owner)
      final def expected(something: String, owner: Class[?]): String =
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

      final def unrecognized[T](culprit: Class[?], value: String): String =
        s"$ReadError: Unrecognized ${culprit.getName} with value `$value`."
      final def unrecognized(culprit: Class[?], owner: Class[?]): String =
        unrecognized(s"`${culprit.getName}`", owner)
      final def unrecognized(something: String, owner: Class[?]): String =
        s"$ReadError: Unrecognized $something in `${owner.getName}`."
    }
  }
}
