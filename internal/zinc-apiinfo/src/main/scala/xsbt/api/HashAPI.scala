/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbt.api

import xsbti.api._

import scala.util.hashing.MurmurHash3
import HashAPI.Hash

object HashAPI {
  type Hash = Int

  def apply(api: ClassLike): Hash = apply(_.hashAPI(api))

  def apply(x: Def): Hash = apply(_.hashDefinition(x))

  def apply(
      doHashing: HashAPI => Unit,
      includePrivate: Boolean = false,
      includeParamNames: Boolean = true,
      includeDefinitions: Boolean = true,
      includeSealedChildren: Boolean = true,
      includePrivateDefsInTrait: Boolean = false
  ): Hash = {
    val hasher = new HashAPI(
      includePrivate,
      includeParamNames,
      includeDefinitions,
      includeSealedChildren,
      includePrivateDefsInTrait
    )

    doHashing(hasher)
    hasher.finalizeHash
  }

}

/**
 * Implements hashing of public API.
 *
 * @param includePrivate        should private definitions be included in a hash sum
 * @param includeParamNames     should parameter names for methods be included in a hash sum
 * @param includeDefinitions    when hashing a structure (e.g. of a class) should hashes of definitions (members)
 *                              be included in a hash sum. Structure can appear as a type (in structural type).
 *                              In that case we always include definitions in a hash sum.
 * @param includeSealedChildren Controls if types of children of sealed class should be included in hash.
 */
final class HashAPI private (
    includePrivate: Boolean,
    includeParamNames: Boolean,
    includeDefinitions: Boolean,
    includeSealedChildren: Boolean,
    includeTraitBreakers: Boolean
) {
  // this constructor variant is for source and binary backwards compatibility with sbt 0.13.0
  def this(includePrivate: Boolean, includeParamNames: Boolean) = {
    // in the old logic we used to always include definitions hence
    // includeDefinitions=true
    this(
      includePrivate,
      includeParamNames,
      includeDefinitions = true,
      includeSealedChildren = true,
      includeTraitBreakers = false
    )
  }

  import scala.collection.mutable
  import MurmurHash3.{ mix, stringHash, unorderedHash }

  private[this] val visitedStructures = visitedMap[Structure]
  private[this] val visitedClassLike = visitedMap[ClassLike]
  private[this] def visitedMap[T] = new mutable.HashMap[T, List[Hash]]
  private[this] def visit[T](map: mutable.Map[T, List[Hash]], t: T)(hashF: T => Unit): Unit = {
    map.put(t, hash :: map.getOrElse(t, Nil)) match {
      case Some(x :: _) => extend(x)
      case _ =>
        hashF(t)
        for (hs <- map(t))
          extend(hs)
        map.put(t, hash :: Nil)
        ()
    }
  }

  private[this] final val ValHash = 1
  private[this] final val VarHash = 2
  private[this] final val DefHash = 3
  private[this] final val ClassDefHash = 4
  private[this] final val TypeDeclHash = 5
  private[this] final val TypeAliasHash = 6

  private[this] final val PublicHash = 30
  private[this] final val ProtectedHash = 31
  private[this] final val PrivateHash = 32
  private[this] final val UnqualifiedHash = 33
  private[this] final val ThisQualifierHash = 34
  private[this] final val IdQualifierHash = 35

  private[this] final val IdPathHash = 20
  private[this] final val SuperHash = 21
  private[this] final val ThisPathHash = 22

  private[this] final val ValueParamsHash = 40
  private[this] final val ClassPendingHash = 41
  private[this] final val StructurePendingHash = 42

  private[this] final val EmptyTypeHash = 51
  private[this] final val ParameterRefHash = 52
  private[this] final val SingletonHash = 53
  private[this] final val ProjectionHash = 54
  private[this] final val ParameterizedHash = 55
  private[this] final val AnnotatedHash = 56
  private[this] final val PolymorphicHash = 57
  private[this] final val ConstantHash = 58
  private[this] final val ExistentialHash = 59
  private[this] final val StructureHash = 60

  private[this] val ClassHash = 70

  private[this] final val TrueHash = 97
  private[this] final val FalseHash = 98

  private[this] var hash: Hash = 0

  final def hashString(s: String): Unit = extend(stringHash(s))
  final def hashBoolean(b: Boolean): Unit = extend(if (b) TrueHash else FalseHash)
  @inline final def hashArray[T <: AnyRef](s: Array[T], hashF: T => Unit): Unit = {
    extend(s.length)
    var i = 0
    while (i < s.length) {
      hashF(s(i))
      i += 1
    }
  }
  final def hashSymmetric[T](ts: TraversableOnce[T], hashF: T => Unit): Unit = {
    val current = hash
    val tsHash: Hash = ts match {
      case ts: collection.Iterable[T] =>
        // Avoid creation of a temporary collection and avoid boxing of hashCodes by passing
        // this iterator to `unorderedHash`. It returns itself each time with a different hashCode.
        class HashIterator(delegate: Iterator[T]) extends Iterator[AnyRef] {
          override def hasNext: Boolean = delegate.hasNext
          def next(): AnyRef = { hash = 1; hashF(delegate.next()); this }
          override def hashCode: Int = finalizeHash
        }
        unorderedHash(new HashIterator(ts.iterator))
      case _ =>
        unorderedHash(ts.toList.map { t =>
          hash = 1
          hashF(t)
          finalizeHash
        })
    }
    hash = current
    extend(tsHash)
  }

  @inline final def extend(a: Hash): Unit = {
    hash = mix(hash, a)
  }

  // TODO: Figure out what should be the length
  def finalizeHash: Hash = MurmurHash3.finalizeHash(hash, 1)

  def hashModifiers(m: Modifiers) = extend(m.raw.toInt)

  def hashAPI(c: ClassLike): Unit = {
    hash = 1
    hashClass(c)
  }

  def hashPackage(p: Package) = hashString(p.name)

  def hashDefinitions(ds: Seq[Definition], topLevel: Boolean, isTrait: Boolean): Unit = {
    // Any private definition in a trait is required to be concrete
    def isPrivate(d: Definition): Boolean =
      d.access match { case _: xsbti.api.Private => true; case _ => false }

    def isTraitBreaker(d: Definition): Boolean = d match {
      // Vars and vals in traits introduce getters, setters and fields in the implementing classes.
      // See test `source-dependencies/trait-private-var
      case _: FieldLike => true
      // Objects in traits introduce fields in the implementing classes.
      // See test `source-dependencies/trait-private-object`
      case cl: ClassLikeDef => cl.definitionType == DefinitionType.Module
      // super calls introduce accessors that are not part of the public API
      case d: Def => d.modifiers.isSuperAccessor
      case _      => false
    }

    val requiresPrivateDefinitions = isTrait && includeTraitBreakers && !topLevel
    // Get private definitions that break trait properties in incremental compilation
    val extraPrivateDefinitions =
      if (!requiresPrivateDefinitions) Nil
      else ds.filter(x => isTraitBreaker(x) && isPrivate(x))

    val defs = SameAPI.filterDefinitions(ds, topLevel, includePrivate)
    hashSymmetric(defs ++ extraPrivateDefinitions, hashDefinition)
  }

  /**
   * Hashes a sequence of definitions by combining each definition's own
   * hash with extra one supplied.
   *
   * It's useful when one wants to influence hash of a definition by some
   * external (to definition) factor (e.g. location of definition).
   *
   * NOTE: This method doesn't perform any filtering of passed definitions.
   */
  def hashDefinitionsWithExtraHash(ds: TraversableOnce[Definition], extraHash: Hash): Unit = {
    def hashDefinitionCombined(d: Definition): Unit = {
      hashDefinition(d)
      extend(extraHash)
    }
    hashSymmetric(ds, hashDefinitionCombined _)
  }
  def hashDefinition(d: Definition): Unit = {
    hashString(d.name)
    hashAnnotations(d.annotations)
    hashModifiers(d.modifiers)
    hashAccess(d.access)
    d match {
      case c: ClassLikeDef    => hashClassDef(c)
      case c: ClassLike       => hashClass(c)
      case f: FieldLike       => hashField(f)
      case d: Def             => hashDef(d)
      case t: TypeDeclaration => hashTypeDeclaration(t)
      case t: TypeAlias       => hashTypeAlias(t)
    }
  }
  final def hashClassDef(c: ClassLikeDef): Unit = {
    extend(ClassDefHash)
    hashParameterizedDefinition(c)
  }
  final def hashClass(c: ClassLike): Unit = visit(visitedClassLike, c)(hashClass0)
  def hashClass0(c: ClassLike): Unit = {
    extend(ClassHash)
    hashTypeParameters(c.typeParameters)
    hashType(c.selfType)
    if (includeSealedChildren)
      hashTypesSymmetric(c.childrenOfSealedClass, includeDefinitions)

    val isTrait = c.definitionType() == DefinitionType.Trait
    hashStructure(c.structure, includeDefinitions, isTrait)
  }
  def hashField(f: FieldLike): Unit = {
    f match {
      case _: Var => extend(VarHash)
      case _: Val => extend(ValHash)
    }
    hashType(f.tpe)
  }
  def hashDef(d: Def): Unit = {
    extend(DefHash)
    hashParameterizedDefinition(d)
    hashValueParameters(d.valueParameters)
    hashType(d.returnType)
  }
  def hashAccess(a: Access): Unit =
    a match {
      case _: Public       => extend(PublicHash)
      case qual: Qualified => hashQualified(qual)
    }
  def hashQualified(qual: Qualified): Unit = {
    qual match {
      case _: Protected => extend(ProtectedHash)
      case _: Private   => extend(PrivateHash)
    }
    hashQualifier(qual.qualifier)
  }
  def hashQualifier(qual: Qualifier): Unit =
    qual match {
      case _: Unqualified   => extend(UnqualifiedHash)
      case _: ThisQualifier => extend(ThisQualifierHash)
      case id: IdQualifier =>
        extend(IdQualifierHash)
        hashString(id.value)
    }

  def hashValueParameters(valueParameters: Array[ParameterList]) =
    hashArray(valueParameters, hashValueParameterList)
  def hashValueParameterList(list: ParameterList) = {
    extend(ValueParamsHash)
    hashBoolean(list.isImplicit)
    hashArray(list.parameters, hashValueParameter)
  }
  def hashValueParameter(parameter: MethodParameter) = {
    hashString(parameter.name)
    hashType(parameter.tpe)
    extend(parameter.modifier.ordinal)
    hashBoolean(parameter.hasDefault)
  }

  def hashParameterizedDefinition[T <: ParameterizedDefinition](d: T): Unit = {
    hashTypeParameters(d.typeParameters)
  }
  def hashTypeDeclaration(d: TypeDeclaration): Unit = {
    extend(TypeDeclHash)
    hashParameterizedDefinition(d)
    hashType(d.lowerBound)
    hashType(d.upperBound)
  }
  def hashTypeAlias(d: TypeAlias): Unit = {
    extend(TypeAliasHash)
    hashParameterizedDefinition(d)
    hashType(d.tpe)
  }

  def hashTypeParameters(parameters: Array[TypeParameter]) =
    hashArray(parameters, hashTypeParameter)
  def hashTypeParameter(parameter: TypeParameter): Unit = {
    hashString(parameter.id)
    extend(parameter.variance.ordinal)
    hashTypeParameters(parameter.typeParameters)
    hashType(parameter.lowerBound)
    hashType(parameter.upperBound)
    hashAnnotations(parameter.annotations)
  }
  def hashAnnotations(annotations: Array[Annotation]) = hashArray(annotations, hashAnnotation)
  def hashAnnotation(annotation: Annotation) = {
    hashType(annotation.base)
    hashAnnotationArguments(annotation.arguments)
  }
  def hashAnnotationArguments(args: Array[AnnotationArgument]) =
    hashArray(args, hashAnnotationArgument)
  def hashAnnotationArgument(arg: AnnotationArgument): Unit = {
    hashString(arg.name)
    hashString(arg.value)
  }

  def hashTypes(ts: Array[Type], includeDefinitions: Boolean = true) =
    hashArray(ts, (t: Type) => hashType(t, includeDefinitions))
  def hashTypesSymmetric(ts: Array[Type], includeDefinitions: Boolean = true) =
    hashSymmetric(ts, hashType(_, includeDefinitions))
  def hashType(t: Type, includeDefinitions: Boolean = true): Unit =
    t match {
      case s: Structure     => hashStructure(s, includeDefinitions, false)
      case e: Existential   => hashExistential(e)
      case c: Constant      => hashConstant(c)
      case p: Polymorphic   => hashPolymorphic(p)
      case a: Annotated     => hashAnnotated(a)
      case p: Parameterized => hashParameterized(p)
      case p: Projection    => hashProjection(p)
      case _: EmptyType     => extend(EmptyTypeHash)
      case s: Singleton     => hashSingleton(s)
      case pr: ParameterRef => hashParameterRef(pr)
    }

  def hashParameterRef(p: ParameterRef): Unit = {
    extend(ParameterRefHash)
    hashString(p.id)
  }
  def hashSingleton(s: Singleton): Unit = {
    extend(SingletonHash)
    hashPath(s.path)
  }
  def hashPath(path: Path) = {
    hashArray(path.components, hashPathComponent)
  }
  def hashPathComponent(pc: PathComponent) = pc match {
    case _: This  => extend(ThisPathHash)
    case s: Super => hashSuperPath(s)
    case id: Id   => hashIdPath(id)
  }
  def hashSuperPath(s: Super): Unit = {
    extend(SuperHash)
    hashPath(s.qualifier)
  }
  def hashIdPath(id: Id): Unit = {
    extend(IdPathHash)
    hashString(id.id)
  }

  def hashConstant(c: Constant) = {
    extend(ConstantHash)
    hashString(c.value)
    hashType(c.baseType)
  }
  def hashExistential(e: Existential) = {
    extend(ExistentialHash)
    hashParameters(e.clause, e.baseType)
  }
  def hashPolymorphic(p: Polymorphic) = {
    extend(PolymorphicHash)
    hashParameters(p.parameters, p.baseType)
  }
  def hashProjection(p: Projection) = {
    extend(ProjectionHash)
    hashString(p.id)
    hashType(p.prefix)
  }
  def hashParameterized(p: Parameterized): Unit = {
    extend(ParameterizedHash)
    hashType(p.baseType)
    hashTypes(p.typeArguments)
  }
  def hashAnnotated(a: Annotated): Unit = {
    extend(AnnotatedHash)
    hashType(a.baseType)
    hashAnnotations(a.annotations)
  }

  def hashStructure(structure: Structure, includeDefinitions: Boolean, isTrait: Boolean) =
    visit(visitedStructures, structure)(hashStructure0(includeDefinitions, isTrait))

  // Hoisted functions to reduce allocation.
  private def hashStructure0(includeDefinitions: Boolean, isTrait: Boolean): (Structure => Unit) = {
    if (isTrait && includeTraitBreakers) hashStructure0WithDefsTrait
    else if (includeDefinitions) hashStructure0WithDefs
    else hashStructure0NoDefs
  }

  private[this] final val hashStructure0WithDefsTrait = (s: Structure) =>
    hashStructure0(s, includeDefinitions = true, isTrait = true)
  private[this] final val hashStructure0WithDefs = (s: Structure) =>
    hashStructure0(s, includeDefinitions = true, isTrait = false)
  private[this] final val hashStructure0NoDefs = (s: Structure) =>
    hashStructure0(s, includeDefinitions = false, isTrait = false)

  def hashStructure0(structure: Structure, includeDefinitions: Boolean, isTrait: Boolean): Unit = {
    extend(StructureHash)
    hashTypes(structure.parents, includeDefinitions)
    if (includeDefinitions) {
      hashDefinitions(structure.declared, false, isTrait)
      hashDefinitions(structure.inherited, false, isTrait)
    }
  }
  def hashParameters(parameters: Array[TypeParameter], base: Type): Unit = {
    hashTypeParameters(parameters)
    hashType(base)
  }
}
