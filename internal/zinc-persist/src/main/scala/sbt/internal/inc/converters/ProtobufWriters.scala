package sbt.internal.inc.converters

import java.io.File

import sbt.internal.inc._
import xsbti.{ Position, Problem, Severity, T2, UseScope }
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
        val groups = multiple0.getOutputGroups.toSchemaList(toOutputGroup)
        val multiple = schema.MultipleOutput(outputGroups = groups)
        CompilationOutput.MultipleOutput(multiple)
      case _ => sys.error(WritersFeedback.UnexpectedEmptyOutput)
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

  def toSourceInfos(sourceInfos0: SourceInfos): schema.SourceInfos = {
    val sourceInfos = sourceInfos0.allInfos.map(kv => kv._1.getAbsolutePath -> toSourceInfo(kv._2))
    schema.SourceInfos(sourceInfos = sourceInfos)
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
        val groups = multiple0.getOutputGroups.toSchemaList(toOutputGroup)
        val multiple = schema.MultipleOutput(outputGroups = groups)
        CompilationOutput.MultipleOutput(multiple)
      case _ => sys.error(WritersFeedback.UnexpectedEmptyOutput)
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

  implicit class EfficientTraverse[T](array: Array[T]) {
    def toSchemaList[R](f: T => R): List[R] =
      array.iterator.map(f).toList
  }

  def toPath(path: Path): schema.Path = {
    def toPathComponent(pathComponent: PathComponent): schema.Path.PathComponent = {
      import schema.Path.{ PathComponent => SchemaPath }
      import SchemaPath.{ Component => SchemaComponent }
      val component = pathComponent match {
        case c: Id    => SchemaComponent.Id(schema.Id(id = c.id))
        case c: Super => SchemaComponent.Super(schema.Super(qualifier = Some(toPath(c.qualifier))))
        case _: This  => SchemaComponent.This(WritersConstants.This)
      }
      SchemaPath(component = component)
    }
    val components = path.components().toSchemaList(toPathComponent)
    schema.Path(components = components)
  }

  def toAnnotation(annotation: Annotation): schema.Annotation = {
    def toAnnotationArgument(annotationArgument: AnnotationArgument): schema.AnnotationArgument = {
      val name = annotationArgument.name()
      val value = annotationArgument.value()
      schema.AnnotationArgument(name = name, value = value)
    }

    val arguments = annotation.arguments().toSchemaList(toAnnotationArgument)
    val base = Some(toType(annotation.base()))
    schema.Annotation(base = base, arguments = arguments)
  }

  def toStructure(tpe: Structure): schema.Type.Structure = {
    val declared = tpe.declared().toSchemaList(toClassDefinition)
    val inherited = tpe.inherited().toSchemaList(toClassDefinition)
    val parents = tpe.parents().toSchemaList(toType)
    schema.Type.Structure(
      declared = declared,
      inherited = inherited,
      parents = parents
    )
  }

  def toType(`type`: Type): schema.Type = {
    def toExistential(tpe: Existential): schema.Type.Existential = {
      val baseType = Some(toType(tpe.baseType()))
      val clause = tpe.clause().toSchemaList(toTypeParameter)
      schema.Type.Existential(baseType = baseType, clause = clause)
    }

    def toProjection(tpe: Projection): schema.Type.Projection = {
      val id = tpe.id()
      val prefix = Some(toType(tpe.prefix()))
      schema.Type.Projection(id = id, prefix = prefix)
    }

    def toPolymorphic(tpe: Polymorphic): schema.Type.Polymorphic = {
      val baseType = Some(toType(tpe.baseType()))
      val parameters = tpe.parameters().toSchemaList(toTypeParameter)
      schema.Type.Polymorphic(baseType = baseType, typeParameters = parameters)
    }

    def toParameterRef(tpe: ParameterRef): schema.Type.ParameterRef = {
      schema.Type.ParameterRef(id = tpe.id())
    }

    def toParameterized(tpe: Parameterized): schema.Type.Parameterized = {
      val baseType = Some(toType(tpe.baseType()))
      val typeArguments = tpe.typeArguments().toSchemaList(toType)
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
      val annotations = tpe.annotations().toSchemaList(toAnnotation)
      val baseType = Some(toType(tpe.baseType()))
      schema.Type.Annotated(baseType = baseType, annotations = annotations)
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
      case _: EmptyType       => schema.Type.Value.EmptyType(value = WritersConstants.EmptyType)
    }

    schema.Type(value = schemaType)
  }

  def toDefinitionType(definitionType: DefinitionType): schema.DefinitionType = {
    definitionType match {
      case DefinitionType.ClassDef      => schema.DefinitionType.CLASSDEF
      case DefinitionType.Module        => schema.DefinitionType.MODULE
      case DefinitionType.Trait         => schema.DefinitionType.TRAIT
      case DefinitionType.PackageModule => schema.DefinitionType.PACKAGEMODULE
    }
  }

  def toAccess(access: Access): schema.Access = {
    def toQualifier(qualifier: Qualifier): schema.Qualifier = {
      import WritersConstants.{ ThisQualifier, Unqualified }
      import schema.Qualifier.{ Type => QualifierType }
      val qualifierType = qualifier match {
        case q: IdQualifier   => QualifierType.IdQualifier(value = schema.IdQualifier(q.value()))
        case _: ThisQualifier => QualifierType.ThisQualifier(value = ThisQualifier)
        case _: Unqualified   => QualifierType.Unqualified(value = Unqualified)
      }
      schema.Qualifier(`type` = qualifierType)
    }
    import WritersConstants.PublicAccess
    import schema.Access.{ Type => AccessType }
    val accessType = access match {
      case _: Public => AccessType.Public(value = PublicAccess)
      case qualified: Qualified =>
        val qualifier = Some(toQualifier(qualified.qualifier()))
        qualified match {
          case _: Private   => AccessType.Private(schema.Private(qualifier = qualifier))
          case _: Protected => AccessType.Protected(schema.Protected(qualifier = qualifier))
        }
    }
    schema.Access(`type` = accessType)
  }

  def toModifiers(modifiers: Modifiers): schema.Modifiers = {
    schema.Modifiers(flags = modifiers.raw().toInt)
  }

  def toClassDefinition(classDefinition: ClassDefinition): schema.ClassDefinition = {

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

      val parameters = parameterList.parameters().toSchemaList(toMethodParameter)
      val isImplicit = parameterList.isImplicit()
      schema.ParameterList(parameters = parameters, isImplicit = isImplicit)
    }

    import schema.ClassDefinition.{ Extra => DefType }
    def toClassLikeDef(classLikeDef: ClassLikeDef): DefType.ClassLikeDef = {
      val definitionType = toDefinitionType(classLikeDef.definitionType())
      val typeParameters = classLikeDef.typeParameters().toSchemaList(toTypeParameter)
      DefType.ClassLikeDef(
        schema.ClassDefinition.ClassLikeDef(
          typeParameters = typeParameters,
          definitionType = definitionType
        )
      )
    }

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
      val typeParameters = defDef.typeParameters().toSchemaList(toTypeParameter)
      val valueParameters = defDef.valueParameters().toSchemaList(toParameterList)
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
      val typeParameters = typeAlias.typeParameters().toSchemaList(toTypeParameter)
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
      val typeParameters = typeDeclaration.typeParameters().toSchemaList(toTypeParameter)
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
    val annotations = classDefinition.annotations().toSchemaList(toAnnotation)
    val extra = classDefinition match {
      case classLikeDef: ClassLikeDef       => toClassLikeDef(classLikeDef)
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
    val annotations = typeParameter.annotations().toSchemaList(toAnnotation)
    val typeParameters = typeParameter.typeParameters().toSchemaList(toTypeParameter)
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

  def toClassLike(classLike: ClassLike): schema.ClassLike = {
    val name = classLike.name()
    val access = Some(toAccess(classLike.access()))
    val modifiers = Some(toModifiers(classLike.modifiers()))
    val annotations = classLike.annotations().toSchemaList(toAnnotation)

    val definitionType = toDefinitionType(classLike.definitionType())
    val selfType = Some(toType(classLike.selfType()))
    val structure = Some(toStructure(classLike.structure()))
    val savedAnnotations = classLike.savedAnnotations()
    val childrenOfSealedClass = classLike.childrenOfSealedClass().toSchemaList(toType)
    val topLevel = classLike.topLevel()
    val typeParameters = classLike.typeParameters().toSchemaList(toTypeParameter)
    schema.ClassLike(
      name = name,
      access = access,
      modifiers = modifiers,
      annotations = annotations,
      definitionType = definitionType,
      selfType = selfType,
      structure = structure,
      savedAnnotations = savedAnnotations,
      childrenOfSealedClass = childrenOfSealedClass,
      topLevel = topLevel,
      typeParameters = typeParameters
    )
  }

  def toUseScope(useScope: UseScope): schema.UseScope = {
    useScope match {
      case UseScope.Default      => schema.UseScope.DEFAULT
      case UseScope.Implicit     => schema.UseScope.IMPLICIT
      case UseScope.PatMatTarget => schema.UseScope.PATMAT
    }
  }

  def toAnalyzedClass(analyzedClass: AnalyzedClass): schema.AnalyzedClass = {
    def toCompanions(companions: Companions): schema.Companions = {
      val classApi = Some(toClassLike(companions.classApi()))
      val objectApi = Some(toClassLike(companions.objectApi()))
      schema.Companions(classApi = classApi, objectApi = objectApi)
    }

    def toNameHash(nameHash: NameHash): schema.NameHash = {
      val name = nameHash.name()
      val useScope = toUseScope(nameHash.scope())
      schema.NameHash(name = name, scope = useScope)
    }

    val companions = Some(toCompanions(analyzedClass.api()))
    val apiHash = analyzedClass.apiHash()
    val compilationTimestamp = analyzedClass.compilationTimestamp()
    val hasMacro = analyzedClass.hasMacro
    val name = analyzedClass.name()
    val nameHashes = analyzedClass.nameHashes().toSchemaList(toNameHash)
    schema.AnalyzedClass(
      compilationTimestamp = compilationTimestamp,
      name = name,
      api = companions,
      apiHash = apiHash,
      nameHashes = nameHashes,
      hasMacro = hasMacro
    )
  }

  private final val fileToString = (f: File) => f.getAbsolutePath
  private final val stringId = identity[String] _
  def toRelations(relations: Relations): schema.Relations = {
    import sbt.internal.util.Relation

    def toUsedName(usedName: UsedName): schema.UsedName = {
      import scala.collection.JavaConverters._
      val name = usedName.name
      val javaIterator = usedName.scopes.iterator()
      val scopes = javaIterator.asScala.map(toUseScope).toList
      schema.UsedName(name = name, scopes = scopes)
    }

    def toUsedNamesMap(relation: Relation[String, UsedName]): Map[String, schema.UsedNames] = {
      relation.forwardMap.mapValues { names =>
        val usedNames = names.iterator.map(toUsedName).toList
        schema.UsedNames(usedNames = usedNames)
      }
    }

    def toMap[K, V](
        relation: Relation[K, V],
        fk: K => String,
        fv: V => String
    ): Map[String, schema.Values] = {
      relation.forwardMap.map {
        case (k, vs) =>
          val fileValues = schema.Values(values = vs.iterator.map(fv).toList)
          fk(k) -> fileValues
      }
    }

    val srcProd = toMap(relations.srcProd, fileToString, fileToString)
    val libraryDep = toMap(relations.libraryDep, fileToString, fileToString)
    val libraryClassName = toMap(relations.libraryClassName, fileToString, stringId)
    val memberRefInternal = toMap(relations.memberRef.internal, stringId, stringId)
    val memberRefExternal = toMap(relations.memberRef.external, stringId, stringId)
    val inheritanceInternal = toMap(relations.inheritance.internal, stringId, stringId)
    val inheritanceExternal = toMap(relations.inheritance.external, stringId, stringId)
    val localInheritanceInternal = toMap(relations.localInheritance.internal, stringId, stringId)
    val localInheritanceExternal = toMap(relations.localInheritance.external, stringId, stringId)
    val classes = toMap(relations.classes, fileToString, stringId)
    val productClassName = toMap(relations.productClassName, stringId, stringId)
    val names = toUsedNamesMap(relations.names)
    val memberRef = Some(
      schema.ClassDependencies(
        internal = memberRefInternal,
        external = memberRefExternal
      ))
    val inheritance = Some(
      schema.ClassDependencies(
        internal = inheritanceInternal,
        external = inheritanceExternal
      ))
    val localInheritance = Some(
      schema.ClassDependencies(
        internal = localInheritanceInternal,
        external = localInheritanceExternal
      ))
    schema.Relations(
      srcProd = srcProd,
      libraryDep = libraryDep,
      libraryClassName = libraryClassName,
      classes = classes,
      productClassName = productClassName,
      names = names,
      memberRef = memberRef,
      inheritance = inheritance,
      localInheritance = localInheritance
    )
  }

  def toAnalysis(analysis: Analysis): schema.Analysis = {
    def toApis(apis: APIs): schema.APIs = {
      val internal = apis.internal.mapValues(toAnalyzedClass)
      val external = apis.external.mapValues(toAnalyzedClass)
      schema.APIs(internal = internal, external = external)
    }

    val stamps = Some(toStamps(analysis.stamps))
    val apis = Some(toApis(analysis.apis))
    val relations = Some(toRelations(analysis.relations))
    val sourceInfos = Some(toSourceInfos(analysis.infos))
    val compilations = Some(toCompilations(analysis.compilations))
    schema.Analysis(
      stamps = stamps,
      apis = apis,
      relations = relations,
      sourceInfos = sourceInfos,
      compilations = compilations
    )
  }
}
