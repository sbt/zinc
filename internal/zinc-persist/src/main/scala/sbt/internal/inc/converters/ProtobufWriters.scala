package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc.{ Compilation, Compilations, Hash, LastModified, Stamps, schema }
import xsbti.{ Position, Problem, Severity, T2 }
import xsbti.compile.analysis.{ SourceInfo, Stamp }
import sbt.internal.inc.converters.ProtobufDefaults.Feedback.{ Writers => WritersFeedback }
import sbt.internal.inc.converters.ProtobufDefaults.WritersConstants
import xsbti.api.{ Private, _ }
import xsbti.compile.{
  CompileOrder,
  FileHash,
  MiniOptions,
  MiniSetup,
  MultipleOutput,
  Output,
  OutputGroup,
  SingleOutput
}

object ProtobufWriters {
  def toStampType(stamp: Stamp): schema.Stamps.StampType = {
    val s0 = schema.Stamps.StampType()
    stamp match {
      case hash: Hash       => s0.withHash(schema.Hash(hash = hash.hexHash))
      case lm: LastModified => s0.withLastModified(schema.LastModified(millis = lm.value))
      case _: Stamp         => s0
    }
  }

  def toStamps(stamps: Stamps): schema.Stamps = {
    def toSchemaMap(data: Map[File, Stamp]): Map[String, schema.Stamps.StampType] =
      data.map(kv => kv._1.getAbsolutePath -> toStampType(kv._2))

    val binaryStamps = toSchemaMap(stamps.binaries)
    val sourceStamps = toSchemaMap(stamps.sources)
    val productStamps = toSchemaMap(stamps.products)
    schema.Stamps(
      binaryStamps = binaryStamps,
      sourceStamps = sourceStamps,
      productStamps = productStamps
    )
  }

  def toOutputGroup(outputGroup: OutputGroup): schema.OutputGroup = {
    val sourcePath = outputGroup.getSourceDirectory.getAbsolutePath
    val targetPath = outputGroup.getOutputDirectory.getAbsolutePath
    schema.OutputGroup(source = sourcePath, target = targetPath)
  }

  def toCompilationOutput(output: Output): schema.Compilation.Output = {
    import schema.Compilation.{ Output => CompilationOutput }
    output match {
      case single0: SingleOutput =>
        val targetPath = single0.getOutputDirectory.getAbsolutePath
        val single = schema.SingleOutput(target = targetPath)
        CompilationOutput.SingleOutput(single)
      case multiple0: MultipleOutput =>
        val groups = multiple0.getOutputGroups.iterator.map(toOutputGroup).toList
        val multiple = schema.MultipleOutput(outputGroups = groups)
        CompilationOutput.MultipleOutput(multiple)
      case unknown => sys.error(WritersFeedback.ExpectedNonEmptyOutput)
    }
  }

  def toCompilation(compilation: Compilation): schema.Compilation = {
    val startTime = compilation.getStartTime
    val output = toCompilationOutput(compilation.getOutput)
    schema.Compilation(startTime = startTime, output = output)
  }

  def toCompilations(compilations0: Compilations): schema.Compilations = {
    val compilations = compilations0.allCompilations.map(toCompilation)
    schema.Compilations(compilations = compilations)
  }

  import ProtobufDefaults.{ MissingString, MissingInt }
  import sbt.internal.inc.JavaInterfaceUtil._
  def toPosition(position: Position): schema.Position = {
    schema.Position(
      line = position.line.toOption.fold(MissingInt)(_.toInt),
      offset = position.offset.toOption.fold(MissingInt)(_.toInt),
      lineContent = position.lineContent,
      pointer = position.pointer.toOption.fold(MissingInt)(_.toInt),
      pointerSpace = position.pointerSpace.toOption.getOrElse(MissingString),
      sourcePath = position.sourcePath.toOption.getOrElse(MissingString),
      sourceFilepath = position.sourceFile.toOption.fold(MissingString)(_.getAbsolutePath)
    )
  }

  def toSeverity(severity: Severity): schema.Severity = {
    severity match {
      case Severity.Info  => schema.Severity.INFO
      case Severity.Warn  => schema.Severity.WARN
      case Severity.Error => schema.Severity.ERROR
    }
  }

  def toProblem(problem: Problem): schema.Problem = {
    val category = problem.category()
    val message = problem.message()
    val position = Option(toPosition(problem.position()))
    val severity = toSeverity(problem.severity())
    schema.Problem(
      category = category,
      message = message,
      position = position,
      severity = severity
    )
  }

  def toSourceInfo(sourceInfo: SourceInfo): schema.SourceInfo = {
    val mainClasses = sourceInfo.getMainClasses
    val reportedProblems = sourceInfo.getReportedProblems.map(toProblem).toSeq
    val unreportedProblems = sourceInfo.getUnreportedProblems.map(toProblem).toSeq
    schema.SourceInfo(
      reportedProblems = reportedProblems,
      unreportedProblems = unreportedProblems,
      mainClasses = mainClasses
    )
  }

  def toFileHash(fileHash: FileHash): schema.FileHash = {
    schema.FileHash(
      path = fileHash.file.getAbsolutePath,
      hash = fileHash.hash
    )
  }

  def toMiniOptions(miniOptions: MiniOptions): schema.MiniOptions = {
    val classpathHash = miniOptions.classpathHash.map(toFileHash)
    val javacOptions = miniOptions.javacOptions()
    val scalacOptions = miniOptions.scalacOptions()
    schema.MiniOptions(
      classpathHash = classpathHash,
      javacOptions = javacOptions,
      scalacOptions = scalacOptions
    )
  }

  def toCompileOrder(compileOrder: CompileOrder): schema.CompileOrder = {
    compileOrder match {
      case CompileOrder.Mixed         => schema.CompileOrder.MIXED
      case CompileOrder.JavaThenScala => schema.CompileOrder.JAVATHENSCALA
      case CompileOrder.ScalaThenJava => schema.CompileOrder.SCALATHENJAVA
    }
  }

  def toStringTuple(tuple: T2[String, String]): schema.Tuple = {
    schema.Tuple(first = tuple.get1(), second = tuple.get2())
  }

  def toMiniSetupOutput(output: Output): schema.MiniSetup.Output = {
    import schema.MiniSetup.{ Output => CompilationOutput }
    output match {
      case single0: SingleOutput =>
        val targetPath = single0.getOutputDirectory.getAbsolutePath
        val single = schema.SingleOutput(target = targetPath)
        CompilationOutput.SingleOutput(single)
      case multiple0: MultipleOutput =>
        val groups = multiple0.getOutputGroups.iterator.map(toOutputGroup).toList
        val multiple = schema.MultipleOutput(outputGroups = groups)
        CompilationOutput.MultipleOutput(multiple)
      case unknown => sys.error(WritersFeedback.ExpectedNonEmptyOutput)
    }
  }

  def toMiniSetup(miniSetup: MiniSetup): schema.MiniSetup = {
    val output = toMiniSetupOutput(miniSetup.output())
    val miniOptions = Some(toMiniOptions(miniSetup.options()))
    val compilerVersion = miniSetup.compilerVersion()
    val compileOrder = toCompileOrder(miniSetup.order())
    val storeApis = miniSetup.storeApis()
    val extra = miniSetup.extra().map(toStringTuple)
    schema.MiniSetup(
      output = output,
      miniOptions = miniOptions,
      compilerVersion = compilerVersion,
      compileOrder = compileOrder,
      storeApis = storeApis,
      extra = extra
    )
  }

  def toPath(path: Path): schema.Path = {
    def toPathComponent(pathComponent: PathComponent): schema.Path.PathComponent = {
      import schema.Path.{ PathComponent => SchemaPath }
      import SchemaPath.{ Component => SchemaComponent }
      val component = pathComponent match {
        case c: Id    => SchemaComponent.Id(schema.Id(id = c.id))
        case c: Super => SchemaComponent.Super(schema.Super(qualifier = Some(toPath(c.qualifier))))
        case c: This  => SchemaComponent.This(WritersConstants.This)
      }
      SchemaPath(component = component)
    }
    val components = path.components().iterator.map(toPathComponent).toList
    schema.Path(components = components)
  }

  def toAnnotation(annotation: Annotation): schema.Annotation = {
    def toAnnotationArgument(annotationArgument: AnnotationArgument): schema.AnnotationArgument = {
      val name = annotationArgument.name()
      val value = annotationArgument.value()
      schema.AnnotationArgument(name = name, value = value)
    }

    val arguments = annotation.arguments().iterator.map(toAnnotationArgument).toList
    val base = Some(toType(annotation.base()))
    schema.Annotation(base = base, arguments = arguments)
  }

  def toType(`type`: Type): schema.Type = {
    def toExistential(tpe: Existential): schema.Type.Existential = {
      val baseType = Some(toType(tpe.baseType()))
      val clause = tpe.clause().iterator.map(toTypeParameter).toList
      schema.Type.Existential(baseType = baseType, clause = clause)
    }

    def toProjection(tpe: Projection): schema.Type.Projection = {
      val id = tpe.id()
      val prefix = Some(toType(tpe.prefix()))
      schema.Type.Projection(id = id, prefix = prefix)
    }

    def toPolymorphic(tpe: Polymorphic): schema.Type.Polymorphic = {
      val baseType = Some(toType(tpe.baseType()))
      val parameters = tpe.parameters().iterator.map(toTypeParameter).toList
      schema.Type.Polymorphic(baseType = baseType, typeParameters = parameters)
    }

    def toParameterRef(tpe: ParameterRef): schema.Type.ParameterRef = {
      schema.Type.ParameterRef(id = tpe.id())
    }

    def toParameterized(tpe: Parameterized): schema.Type.Parameterized = {
      val baseType = Some(toType(tpe.baseType()))
      val typeArguments = tpe.typeArguments().iterator.map(toType).toList
      schema.Type.Parameterized(baseType = baseType, typeArguments = typeArguments)
    }

    def toSingleton(tpe: Singleton): schema.Type.Singleton = {
      val path = Some(toPath(tpe.path()))
      schema.Type.Singleton(path = path)
    }

    def toConstant(tpe: Constant): schema.Type.Constant = {
      val baseType = Some(toType(tpe.baseType()))
      val value = tpe.value()
      schema.Type.Constant(baseType = baseType, value = value)
    }

    def toAnnotated(tpe: Annotated): schema.Type.Annotated = {
      val annotations = tpe.annotations().iterator.map(toAnnotation).toList
      val baseType = Some(toType(tpe.baseType()))
      schema.Type.Annotated(baseType = baseType, annotations = annotations)
    }

    def toStructure(tpe: Structure): schema.Type.Structure = {
      val declared = tpe.declared().iterator.map(toClassDefinition).toList
      val inherited = tpe.inherited().iterator.map(toClassDefinition).toList
      val parents = tpe.parents().iterator.map(toType).toList
      schema.Type.Structure(
        declared = declared,
        inherited = inherited,
        parents = parents
      )
    }

    val schemaType = `type` match {
      case tpe: ParameterRef  => schema.Type.Value.ParameterRef(value = toParameterRef(tpe))
      case tpe: Parameterized => schema.Type.Value.Parameterized(value = toParameterized(tpe))
      case tpe: Structure     => schema.Type.Value.Structure(value = toStructure(tpe))
      case tpe: Polymorphic   => schema.Type.Value.Polymorphic(value = toPolymorphic(tpe))
      case tpe: Constant      => schema.Type.Value.Constant(value = toConstant(tpe))
      case tpe: Existential   => schema.Type.Value.Existential(value = toExistential(tpe))
      case tpe: Singleton     => schema.Type.Value.Singleton(value = toSingleton(tpe))
      case tpe: Projection    => schema.Type.Value.Projection(value = toProjection(tpe))
      case tpe: Annotated     => schema.Type.Value.Annotated(value = toAnnotated(tpe))
      case tpe: EmptyType     => schema.Type.Value.EmptyType(value = WritersConstants.EmptyType)
    }

    schema.Type(value = schemaType)
  }

  def toClassDefinition(classDefinition: ClassDefinition): schema.ClassDefinition = {
    def toAccess(access: Access): schema.Access = {
      def toQualifier(qualifier: Qualifier): schema.Qualifier = {
        import WritersConstants.{ ThisQualifier, Unqualified }
        import schema.Qualifier.{ Type => QualifierType }
        val qualifierType = qualifier match {
          case q: IdQualifier   => QualifierType.IdQualifier(value = schema.IdQualifier(q.value()))
          case q: ThisQualifier => QualifierType.ThisQualifier(value = ThisQualifier)
          case q: Unqualified   => QualifierType.Unqualified(value = Unqualified)
        }
        schema.Qualifier(`type` = qualifierType)
      }
      import WritersConstants.PublicAccess
      import schema.Access.{ Type => AccessType }
      val accessType = access match {
        case a: Public => AccessType.Public(value = PublicAccess)
        case qualified: Qualified =>
          val qualifier = Some(toQualifier(qualified.qualifier()))
          qualified match {
            case _: Private   => AccessType.Private(schema.Private(qualifier = qualifier))
            case _: Protected => AccessType.Protected(schema.Protected(qualifier = qualifier))
          }
      }
      schema.Access(`type` = accessType)
    }

    def toModifiers(modifiers: Modifiers): schema.Modifiers =
      schema.Modifiers(flags = modifiers.raw().toInt)

    def toParameterList(parameterList: ParameterList): schema.ParameterList = {
      def toMethodParameter(methodParameter: MethodParameter): schema.MethodParameter = {
        def toParameterModifier(modifier: ParameterModifier): schema.ParameterModifier = {
          modifier match {
            case ParameterModifier.Plain    => schema.ParameterModifier.PLAIN
            case ParameterModifier.ByName   => schema.ParameterModifier.BYNAME
            case ParameterModifier.Repeated => schema.ParameterModifier.REPEATED
          }
        }
        val name = methodParameter.name()
        val hasDefault = methodParameter.hasDefault()
        val `type` = Some(toType(methodParameter.tpe()))
        val modifier = toParameterModifier(methodParameter.modifier())
        schema.MethodParameter(
          name = name,
          `type` = `type`,
          hasDefault = hasDefault,
          modifier = modifier
        )
      }

      val parameters = parameterList.parameters().iterator.map(toMethodParameter).toList
      val isImplicit = parameterList.isImplicit()
      schema.ParameterList(parameters = parameters, isImplicit = isImplicit)
    }

    import schema.ClassDefinition.{ Extra => DefType }
    def toValDef(valDef: Val): DefType.ValDef = {
      val `type` = Some(toType(valDef.tpe))
      DefType.ValDef(schema.ClassDefinition.Val(`type` = `type`))
    }

    def toVarDef(varDef: Var): DefType.VarDef = {
      val `type` = Some(toType(varDef.tpe))
      DefType.VarDef(schema.ClassDefinition.Var(`type` = `type`))
    }

    def toDefDef(defDef: Def): DefType.DefDef = {
      val returnType = Some(toType(defDef.returnType))
      val typeParameters = defDef.typeParameters().iterator.map(toTypeParameter).toList
      val valueParameters = defDef.valueParameters().iterator.map(toParameterList).toList
      DefType.DefDef(
        schema.ClassDefinition.Def(
          typeParameters = typeParameters,
          valueParameters = valueParameters,
          returnType = returnType
        )
      )
    }

    def toTypeAlias(typeAlias: TypeAlias): DefType.TypeAlias = {
      val `type` = Some(toType(typeAlias.tpe()))
      val typeParameters = typeAlias.typeParameters().iterator.map(toTypeParameter).toList
      DefType.TypeAlias(
        schema.ClassDefinition.TypeAlias(
          `type` = `type`,
          typeParameters = typeParameters
        )
      )
    }

    def toTypeDeclaration(typeDeclaration: TypeDeclaration): DefType.TypeDeclaration = {
      val lowerBound = Some(toType(typeDeclaration.lowerBound()))
      val upperBound = Some(toType(typeDeclaration.upperBound()))
      val typeParameters = typeDeclaration.typeParameters().iterator.map(toTypeParameter).toList
      DefType.TypeDeclaration(
        schema.ClassDefinition.TypeDeclaration(
          lowerBound = lowerBound,
          upperBound = upperBound,
          typeParameters = typeParameters
        )
      )
    }

    val name = classDefinition.name()
    val access = Some(toAccess(classDefinition.access()))
    val modifiers = Some(toModifiers(classDefinition.modifiers()))
    val annotations = classDefinition.annotations().iterator.map(toAnnotation).toList
    val extra = classDefinition match {
      case valDef: Val                      => toValDef(valDef)
      case varDef: Var                      => toVarDef(varDef)
      case defDef: Def                      => toDefDef(defDef)
      case typeAlias: TypeAlias             => toTypeAlias(typeAlias)
      case typeDeclaration: TypeDeclaration => toTypeDeclaration(typeDeclaration)
    }

    schema.ClassDefinition(
      name = name,
      access = access,
      modifiers = modifiers,
      annotations = annotations,
      extra = extra
    )
  }

  def toTypeParameter(typeParameter: TypeParameter): schema.TypeParameter = {
    def toVariance(variance: Variance): schema.Variance = {
      variance match {
        case Variance.Invariant     => schema.Variance.INVARIANT
        case Variance.Covariant     => schema.Variance.COVARIANT
        case Variance.Contravariant => schema.Variance.CONTRAVARIANT
      }
    }

    val id = typeParameter.id()
    val annotations = typeParameter.annotations().iterator.map(toAnnotation).toList
    val typeParameters = typeParameter.typeParameters().iterator.map(toTypeParameter).toList
    val variance = toVariance(typeParameter.variance())
    val lowerBound = Some(toType(typeParameter.lowerBound()))
    val upperBound = Some(toType(typeParameter.upperBound()))
    schema.TypeParameter(
      id = id,
      annotations = annotations,
      typeParameters = typeParameters,
      variance = variance,
      lowerBound = lowerBound,
      upperBound = upperBound
    )
  }
}
