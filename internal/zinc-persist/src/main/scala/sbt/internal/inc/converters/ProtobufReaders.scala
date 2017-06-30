package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc.{
  Compilations,
  ConcreteMultipleOutput,
  ConcreteSingleOutput,
  SimpleOutputGroup,
  SourceInfos,
  Stamps,
  schema
}
import sbt.util.InterfaceUtil
import xsbti.{ Position, Problem, Severity, T2 }
import xsbti.compile.{ CompileOrder, FileHash, MiniOptions, MiniSetup, Output, OutputGroup }
import xsbti.compile.analysis.{ Compilation, SourceInfo, Stamp }
import sbt.internal.inc.converters.ProtobufDefaults.Feedback.{ Readers => ReadersFeedback }
import sbt.internal.inc.converters.ProtobufDefaults.Classes._
import sbt.internal.inc.converters.ProtobufDefaults.ReadersConstants
import xsbti.api._

object ProtobufReaders {
  def fromStampType(stampType: schema.Stamps.StampType): Stamp = {
    import sbt.internal.inc.{ EmptyStamp, LastModified, Hash }
    stampType.`type` match {
      case schema.Stamps.StampType.Type.Empty            => EmptyStamp
      case schema.Stamps.StampType.Type.Hash(h)          => new Hash(h.hash)
      case schema.Stamps.StampType.Type.LastModified(lm) => new LastModified(lm.millis)
      // ^ TODO: Double check that we recompute millis when reading this in certain conditions
    }
  }

  def fromStamps(stamps: schema.Stamps): Stamps = {
    def fromSchemaMap(stamps: Map[String, schema.Stamps.StampType]): Map[File, Stamp] =
      stamps.map(kv => new File(kv._1) -> fromStampType(kv._2))
    val binaries = fromSchemaMap(stamps.binaryStamps)
    val sources = fromSchemaMap(stamps.sourceStamps)
    val products = fromSchemaMap(stamps.productStamps)
    Stamps(
      binaries = binaries,
      sources = sources,
      products = products
    )
  }

  def fromOutputGroup(outputGroup: schema.OutputGroup): OutputGroup = {
    val source = new File(outputGroup.source)
    val target = new File(outputGroup.target)
    SimpleOutputGroup(source, target)
  }

  def fromCompilationOutput(output: schema.Compilation.Output): Output = {
    import schema.Compilation.{ Output => CompilationOutput }
    output match {
      case CompilationOutput.SingleOutput(single) =>
        val target = new File(single.target)
        new ConcreteSingleOutput(target)
      case CompilationOutput.MultipleOutput(multiple) =>
        val groups = multiple.outputGroups.iterator.map(fromOutputGroup).toArray
        new ConcreteMultipleOutput(groups)
      case CompilationOutput.Empty =>
        sys.error(ReadersFeedback.ExpectedNonEmptyOutput)
    }
  }

  def fromCompilation(compilation: schema.Compilation): Compilation = {
    val output = fromCompilationOutput(compilation.output)
    new sbt.internal.inc.Compilation(compilation.startTime, output)
  }

  def fromCompilations(compilations0: schema.Compilations): Compilations = {
    val compilations = compilations0.compilations.map(fromCompilation).toList
    val castedCompilations = compilations.map { case c: sbt.internal.inc.Compilation => c }
    Compilations.make(castedCompilations)
  }

  def fromPosition(position: schema.Position): Position = {
    import ProtobufDefaults.{ MissingString, MissingInt }
    def fromString(value: String): Option[String] =
      if (value == MissingString) None else Some(value)
    def fromInt(value: Int): Option[Integer] =
      if (value == MissingInt) None else Some(value)
    InterfaceUtil.position(
      line0 = fromInt(position.line),
      content = position.lineContent,
      offset0 = fromInt(position.offset),
      pointer0 = fromInt(position.pointer),
      pointerSpace0 = fromString(position.pointerSpace),
      sourcePath0 = fromString(position.sourcePath),
      sourceFile0 = fromString(position.sourceFilepath).map(new File(_))
    )
  }

  def fromSeverity(severity: schema.Severity): Severity = {
    severity match {
      case schema.Severity.INFO             => Severity.Info
      case schema.Severity.WARN             => Severity.Warn
      case schema.Severity.ERROR            => Severity.Error
      case schema.Severity.Unrecognized(id) => sys.error(ReadersFeedback.unrecognizedSeverity(id))
    }
  }

  def fromProblem(problem: schema.Problem): Problem = {
    val category = problem.category
    val message = problem.message
    val severity = fromSeverity(problem.severity)
    val position = problem.position
      .map(fromPosition)
      .getOrElse(sys.error(ReadersFeedback.ExpectedPositionInProblem))
    InterfaceUtil.problem(category, position, message, severity)
  }

  def fromSourceInfo(sourceInfo: schema.SourceInfo): SourceInfo = {
    val mainClasses = sourceInfo.mainClasses
    val reportedProblems = sourceInfo.reportedProblems.map(fromProblem)
    val unreportedProblems = sourceInfo.unreportedProblems.map(fromProblem)
    SourceInfos.makeInfo(reported = reportedProblems,
                         unreported = unreportedProblems,
                         mainClasses = mainClasses)
  }

  def fromFileHash(fileHash: schema.FileHash): FileHash = {
    val file = new File(fileHash.path)
    new FileHash(file, fileHash.hash)
  }

  def fromMiniOptions(miniOptions: schema.MiniOptions): MiniOptions = {
    val classpathHash = miniOptions.classpathHash.map(fromFileHash).toArray
    val javacOptions = miniOptions.javacOptions.toArray
    val scalacOptions = miniOptions.scalacOptions.toArray
    new MiniOptions(classpathHash, scalacOptions, javacOptions)
  }

  def fromCompileOrder(compileOrder: schema.CompileOrder): CompileOrder = {
    compileOrder match {
      case schema.CompileOrder.MIXED            => CompileOrder.Mixed
      case schema.CompileOrder.JAVATHENSCALA    => CompileOrder.JavaThenScala
      case schema.CompileOrder.SCALATHENJAVA    => CompileOrder.ScalaThenJava
      case schema.CompileOrder.Unrecognized(id) => sys.error(ReadersFeedback.unrecognizedOrder(id))
    }
  }

  def fromStringTuple(tuple: schema.Tuple): T2[String, String] = {
    InterfaceUtil.t2(tuple.first -> tuple.second)
  }

  def fromMiniSetupOutput(output: schema.MiniSetup.Output): Output = {
    import schema.MiniSetup.{ Output => MiniSetupOutput }
    output match {
      case MiniSetupOutput.SingleOutput(single) =>
        val target = new File(single.target)
        new ConcreteSingleOutput(target)
      case MiniSetupOutput.MultipleOutput(multiple) =>
        val groups = multiple.outputGroups.iterator.map(fromOutputGroup).toArray
        new ConcreteMultipleOutput(groups)
      case MiniSetupOutput.Empty =>
        sys.error(ReadersFeedback.ExpectedNonEmptyOutput)
    }
  }

  def fromMiniSetup(miniSetup: schema.MiniSetup): MiniSetup = {
    val output = fromMiniSetupOutput(miniSetup.output)
    val miniOptions = miniSetup.miniOptions
      .map(fromMiniOptions)
      .getOrElse(sys.error(ReadersFeedback.MissingMiniOptions))
    val compilerVersion = miniSetup.compilerVersion
    val compileOrder = fromCompileOrder(miniSetup.compileOrder)
    val storeApis = miniSetup.storeApis
    val extra = miniSetup.extra.map(fromStringTuple).toArray
    new MiniSetup(output, miniOptions, compilerVersion, compileOrder, storeApis, extra)
  }

  implicit class EfficientTraverse[T](seq: Seq[T]) {
    def toZincArray[R: scala.reflect.ClassTag](f: T => R): Array[R] =
      seq.iterator.map(f).toArray
  }

  implicit class OptionReader[T](option: Option[T]) {
    def read[R](from: T => R, errorMessage: => String) =
      option.fold(sys.error(errorMessage))(from)
  }

  def fromPath(path: schema.Path): Path = {
    def fromPathComponent(pathComponent: schema.Path.PathComponent): PathComponent = {
      import ReadersFeedback.MissingPathInSuper
      import schema.Path.{ PathComponent => SchemaPath }
      import SchemaPath.{ Component => SchemaComponent }
      pathComponent.component match {
        case SchemaComponent.Id(c)    => new Id(c.id)
        case SchemaComponent.Super(c) => new Super(c.qualifier.read(fromPath, MissingPathInSuper))
        case SchemaComponent.This(_)  => ReadersConstants.This
        case SchemaComponent.Empty =>
          sys.error(ReadersFeedback.missing(ComponentClazz, PathComponentClazz))
      }
    }
    val components = path.components.toZincArray(fromPathComponent)
    new Path(components)
  }

  def fromAnnotation(annotation: schema.Annotation): Annotation = {
    def fromAnnotationArgument(argument: schema.AnnotationArgument): AnnotationArgument = {
      val name = argument.name
      val value = argument.value
      new AnnotationArgument(name, value)
    }

    val arguments = annotation.arguments.toZincArray(fromAnnotationArgument)
    val base = annotation.base.read(fromType, ReadersFeedback.missingBaseIn(AnnotationClazz))
    new Annotation(base, arguments)
  }

  def fromType(`type`: schema.Type): Type = {
    def fromParameterRef(tpe: schema.Type.ParameterRef): ParameterRef = {
      new ParameterRef(tpe.id)
    }

    def fromParameterized(tpe: schema.Type.Parameterized): Parameterized = {
      val baseType = tpe.baseType.read(fromType, ReadersFeedback.missingBaseIn(ParameterizedClazz))
      val typeArguments = tpe.typeArguments.toZincArray(fromType)
      new Parameterized(baseType, typeArguments)
    }

    def fromStructure(tpe: schema.Type.Structure): Structure = {
      def `lazy`[T](value: T): Lazy[T] = SafeLazyProxy.strict(value)
      val parents = `lazy`(tpe.parents.toZincArray(fromType))
      val declared = `lazy`(tpe.declared.toZincArray(fromClassDefinition))
      val inherited = `lazy`(tpe.inherited.toZincArray(fromClassDefinition))
      new Structure(parents, declared, inherited)
    }

    def fromPolymorphic(tpe: schema.Type.Polymorphic): Polymorphic = {
      val baseType = tpe.baseType.read(fromType, ReadersFeedback.missingBaseIn(PolymorphicClazz))
      val typeParameters = tpe.typeParameters.toZincArray(fromTypeParameter)
      new Polymorphic(baseType, typeParameters)
    }

    def fromConstant(tpe: schema.Type.Constant): Constant = {
      val baseType = tpe.baseType.read(fromType, ReadersFeedback.missingBaseIn(ConstantClazz))
      val value = tpe.value
      new Constant(baseType, value)
    }

    def fromExistential(tpe: schema.Type.Existential): Existential = {
      val baseType = tpe.baseType.read(fromType, ReadersFeedback.missingBaseIn(ExistentialClazz))
      val clause = tpe.clause.toZincArray(fromTypeParameter)
      new Existential(baseType, clause)
    }

    def fromSingleton(tpe: schema.Type.Singleton): Singleton = {
      val path = tpe.path.read(fromPath, ReadersFeedback.MissingPathInSingleton)
      new Singleton(path)
    }

    def fromProjection(tpe: schema.Type.Projection): Projection = {
      val id = tpe.id
      val prefix = tpe.prefix.read(fromType, ReadersFeedback.MissingPrefixInProjection)
      new Projection(prefix, id)
    }

    def fromAnnotated(tpe: schema.Type.Annotated): Annotated = {
      val baseType = tpe.baseType.read(fromType, ReadersFeedback.missingBaseIn(AnnotatedClazz))
      val annotations = tpe.annotations.toZincArray(fromAnnotation)
      new Annotated(baseType, annotations)
    }

    `type`.value match {
      case schema.Type.Value.ParameterRef(tpe)  => fromParameterRef(tpe)
      case schema.Type.Value.Parameterized(tpe) => fromParameterized(tpe)
      case schema.Type.Value.Structure(tpe)     => fromStructure(tpe)
      case schema.Type.Value.Polymorphic(tpe)   => fromPolymorphic(tpe)
      case schema.Type.Value.Constant(tpe)      => fromConstant(tpe)
      case schema.Type.Value.Existential(tpe)   => fromExistential(tpe)
      case schema.Type.Value.Singleton(tpe)     => fromSingleton(tpe)
      case schema.Type.Value.Projection(tpe)    => fromProjection(tpe)
      case schema.Type.Value.Annotated(tpe)     => fromAnnotated(tpe)
      case schema.Type.Value.EmptyType(_)       => ReadersConstants.EmptyType
      case schema.Type.Value.Empty              => sys.error(ReadersFeedback.UnexpectedEmptyType)
    }
  }

  def fromClassDefinition(classDefinition: schema.ClassDefinition): ClassDefinition = {
    def fromAccess(access: schema.Access): Access = {
      def fromQualifier(qualifier: schema.Qualifier): Qualifier = {
        import schema.Qualifier.{ Type => QualifierType }
        qualifier.`type` match {
          case QualifierType.IdQualifier(q)   => new IdQualifier(q.value)
          case QualifierType.ThisQualifier(_) => ReadersConstants.ThisQualifier
          case QualifierType.Unqualified(_)   => ReadersConstants.Unqualified
          case QualifierType.Empty            => sys.error(ReadersFeedback.UnexpectedEmptyQualifier)
        }
      }

      def readQualifier(qualifier: Option[schema.Qualifier]): Qualifier =
        qualifier.read(fromQualifier, ReadersFeedback.MissingQualifierInAccess)

      access.`type` match {
        case schema.Access.Type.Public(_)    => ReadersConstants.Public
        case schema.Access.Type.Protected(a) => new Protected(readQualifier(a.qualifier))
        case schema.Access.Type.Private(a)   => new Private(readQualifier(a.qualifier))
        case schema.Access.Type.Empty        => sys.error(ReadersFeedback.UnexpectedAccessType)
      }
    }

    def fromModifiers(modifiers: schema.Modifiers): Modifiers =
      InternalApiProxy.Modifiers(modifiers.flags)

    import ReadersFeedback.{ MissingModifiersInDef, MissingAccessInDef, missingTypeIn }
    import ReadersFeedback.{ missingReturnTypeIn, missingLowerBoundIn, missingUpperBoundIn }
    val name = classDefinition.name
    val access = classDefinition.access.read(fromAccess, MissingAccessInDef)
    val modifiers = classDefinition.modifiers.read(fromModifiers, MissingModifiersInDef)
    val annotations = classDefinition.annotations.toZincArray(fromAnnotation)

    def fromParameterList(parameterList: schema.ParameterList): ParameterList = {
      def fromMethodParameter(methodParameter: schema.MethodParameter): MethodParameter = {
        def fromParameterModifier(modifier: schema.ParameterModifier): ParameterModifier = {
          modifier match {
            case schema.ParameterModifier.PLAIN    => ParameterModifier.Plain
            case schema.ParameterModifier.BYNAME   => ParameterModifier.ByName
            case schema.ParameterModifier.REPEATED => ParameterModifier.Repeated
            case schema.ParameterModifier.Unrecognized(_) =>
              sys.error(ReadersFeedback.UnrecognizedParamModifier)
          }
        }
        val name = methodParameter.name
        val hasDefault = methodParameter.hasDefault
        val `type` = methodParameter.`type`.read(fromType, missingTypeIn(MethodParamClazz))
        val modifier = fromParameterModifier(methodParameter.modifier)
        new MethodParameter(name, `type`, hasDefault, modifier)
      }

      val isImplicit = parameterList.isImplicit
      val parameters = parameterList.parameters.toZincArray(fromMethodParameter)
      new ParameterList(parameters, isImplicit)
    }

    def fromClassLikeDef(defDef: schema.ClassDefinition.ClassLikeDef): ClassLikeDef = {
      def fromDefinitionType(definitionType: schema.DefinitionType): DefinitionType = {
        definitionType match {
          case schema.DefinitionType.CLASSDEF      => DefinitionType.ClassDef
          case schema.DefinitionType.MODULE        => DefinitionType.Module
          case schema.DefinitionType.TRAIT         => DefinitionType.Trait
          case schema.DefinitionType.PACKAGEMODULE => DefinitionType.PackageModule
          case schema.DefinitionType.Unrecognized(_) =>
            sys.error(ReadersFeedback.UnrecognizedDefinitionType)
        }
      }

      val definitionType = fromDefinitionType(defDef.definitionType)
      val typeParameters = defDef.typeParameters.toZincArray(fromTypeParameter)
      new ClassLikeDef(name, access, modifiers, annotations, typeParameters, definitionType)
    }

    def fromDefDef(defDef: schema.ClassDefinition.Def): Def = {
      val returnType = defDef.returnType.read(fromType, missingReturnTypeIn(DefClazz))
      val typeParameters = defDef.typeParameters.toZincArray(fromTypeParameter)
      val valueParameters = defDef.valueParameters.toZincArray(fromParameterList)
      new Def(name, access, modifiers, annotations, typeParameters, valueParameters, returnType)
    }

    def fromValDef(valDef: schema.ClassDefinition.Val): Val = {
      val `type` = valDef.`type`.read(fromType, missingTypeIn(ValClazz))
      new Val(name, access, modifiers, annotations, `type`)
    }

    def fromVarDef(varDef: schema.ClassDefinition.Var): Var = {
      val `type` = varDef.`type`.read(fromType, missingTypeIn(VarClazz))
      new Var(name, access, modifiers, annotations, `type`)
    }

    def fromTypeAlias(typeAlias: schema.ClassDefinition.TypeAlias): TypeAlias = {
      val `type` = typeAlias.`type`.read(fromType, missingTypeIn(TypeAliasClazz))
      val typeParameters = typeAlias.typeParameters.toZincArray(fromTypeParameter)
      new TypeAlias(name, access, modifiers, annotations, typeParameters, `type`)
    }

    def fromTypeDeclaration(decl: schema.ClassDefinition.TypeDeclaration): TypeDeclaration = {
      val lowerBound = decl.lowerBound.read(fromType, missingLowerBoundIn(TypeDeclarationClazz))
      val upperBound = decl.upperBound.read(fromType, missingUpperBoundIn(TypeDeclarationClazz))
      val typeParams = decl.typeParameters.toZincArray(fromTypeParameter)
      new TypeDeclaration(name, access, modifiers, annotations, typeParams, lowerBound, upperBound)
    }

    import schema.ClassDefinition.{ Extra => DefType }
    classDefinition.extra match {
      case DefType.ClassLikeDef(d)    => fromClassLikeDef(d)
      case DefType.DefDef(d)          => fromDefDef(d)
      case DefType.ValDef(d)          => fromValDef(d)
      case DefType.VarDef(d)          => fromVarDef(d)
      case DefType.TypeAlias(d)       => fromTypeAlias(d)
      case DefType.TypeDeclaration(d) => fromTypeDeclaration(d)
      case DefType.Empty              => sys.error(ReadersFeedback.UnexpectedEmptyClassDefinition)
    }
  }

  def fromTypeParameter(typeParameter: schema.TypeParameter): TypeParameter = {
    def fromVariance(variance: schema.Variance): Variance = {
      variance match {
        case schema.Variance.INVARIANT       => Variance.Invariant
        case schema.Variance.COVARIANT       => Variance.Covariant
        case schema.Variance.CONTRAVARIANT   => Variance.Contravariant
        case schema.Variance.Unrecognized(_) => sys.error(ReadersFeedback.UnrecognizedVariance)
      }
    }

    val id = typeParameter.id
    val annotations = typeParameter.annotations.toZincArray(fromAnnotation)
    val typeParameters = typeParameter.typeParameters.toZincArray(fromTypeParameter)
    val variance = fromVariance(typeParameter.variance)
    val lowerBound =
      typeParameter.lowerBound.read(fromType, ReadersFeedback.MissingLowerBoundInParam)
    val upperBound =
      typeParameter.upperBound.read(fromType, ReadersFeedback.MissingUpperBoundInParam)
    new TypeParameter(id, annotations, typeParameters, variance, lowerBound, upperBound)
  }
}
