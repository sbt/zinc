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

package xsbt.api

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import xsbti.UseScope
import xsbti.api.*

/**
 * `HashAPI` and `NameHashing` agree with their legacy copies. The generated classes share
 * structures between members, and members refer back to their own structure, as the visited maps
 * that `HashAPI` keeps across a name group make a hash depend on the order it visits them.
 */
object HashingEquivalenceSpecification extends Properties:
  import HashingEquivalenceGenerators.*

  /** The generators share mutable members between structures, so shrinking would not replay them. */
  override def tests: List[Test] = List(
    property("apiHash", propApiHash),
    property("extraHash", propExtraHash),
    property("nameHashes", propNameHashes),
    property("nameHashes with one instance across classes", propSharedNameHashing),
  ).map(_.withTests(500).noShrinking)

  private def sorted(nameHashes: Array[NameHash]): List[(String, UseScope, Int)] =
    nameHashes.toList.map(nh => (nh.name, nh.scope, nh.hash)).sortBy(nh => (nh._1, nh._2.ordinal))

  def propApiHash: Property =
    for c <- genClass.forAll
    yield LegacyHashAPI(c) ==== HashAPI(c)

  def propExtraHash: Property =
    for c <- genClass.forAll
    yield LegacyHashAPI(_.hashAPI(c), includePrivateDefsInTrait = true) ====
      HashAPI(_.hashAPI(c), includePrivateDefsInTrait = true)

  def propNameHashes: Property =
    for
      c <- genClass.forAll
      optimizedSealed <- Gen.boolean.forAll
    yield sorted(new LegacyNameHashing(optimizedSealed).nameHashes(c)) ====
      sorted(new NameHashing(optimizedSealed).nameHashes(c))

  def propSharedNameHashing: Property =
    for
      cs <- Gen.list(genClass, Range.singleton(3)).forAll
      optimizedSealed <- Gen.boolean.forAll
    yield
      val nameHashing = new NameHashing(optimizedSealed)
      Result.all(cs.map { c =>
        sorted(new LegacyNameHashing(optimizedSealed).nameHashes(c)) ====
          sorted(nameHashing.nameHashes(c))
      })
end HashingEquivalenceSpecification

object HashingEquivalenceGenerators:
  private def zip[A, B](a: Gen[A], b: Gen[B]): Gen[(A, B)] =
    for
      x <- a
      y <- b
    yield (x, y)

  private def zip[A, B, C](a: Gen[A], b: Gen[B], c: Gen[C]): Gen[(A, B, C)] =
    for
      x <- a
      y <- b
      z <- c
    yield (x, y, z)

  /** Like `Gen.frequency1`, but skips the alternatives of weight 0. */
  private def frequency[A](alternatives: (Int, Gen[A])*): Gen[A] =
    Gen.frequencyUnsafe(alternatives.filter(_._1 > 0).toList)

  private final class Ref[T](holder: Array[T], i: Int) extends Lazy[T]:
    def get: T = holder(i)

  private final class Strict[T](t: T) extends Lazy[T]:
    def get: T = t

  // Few names, some qualified, so that name groups hold several definitions.
  val genName: Gen[String] =
    Gen.element1("a", "b", "Foo", "x.Foo", "y.Foo", "x.a", "apply", "<init>")

  val genQualifier: Gen[Qualifier] =
    Gen.element1[Qualifier](Unqualified.of(), ThisQualifier.of(), IdQualifier.of("x"))

  val genAccess: Gen[Access] = Gen.frequency1[Access](
    4 -> Gen.constant(Public.of()),
    1 -> genQualifier.map(Private.of),
    1 -> genQualifier.map(Protected.of),
  )

  val genModifiers: Gen[Modifiers] =
    for
      bits <- Gen.list(
        Gen.frequency1(3 -> Gen.constant(false), 1 -> Gen.constant(true)),
        Range.singleton(8)
      )
    yield new Modifiers(bits(0), bits(1), bits(2), bits(3), bits(4), bits(5), bits(6), bits(7))

  def genArray[T: scala.reflect.ClassTag](g: Gen[T], max: Int): Gen[Array[T]] =
    Gen.list(g, Range.constant(0, max)).map(_.toArray)

  /** The structures a type at this point may refer to. */
  final case class Scope(depth: Int, structures: Vector[Structure])

  def genAnnotation(s: Scope): Gen[Annotation] =
    for
      base <- genLeafType
      args <- genArray(zip(genName, Gen.element1("1", "2")).map(AnnotationArgument.of(_, _)), 2)
    yield Annotation.of(base, args)

  val genPath: Gen[Path] =
    genArray(
      Gen.element1[PathComponent](Id.of("x"), Id.of("y"), This.of(), Super.of(Path.of(Array()))),
      2
    )
      .map(cs => Path.of(cs.asInstanceOf[Array[PathComponent]]))

  val genLeafType: Gen[Type] = Gen.choice1[Type](
    genName.map(Projection.of(EmptyType.of(), _)),
    Gen.element1("A", "B").map(ParameterRef.of),
    Gen.constant(EmptyType.of()),
    genPath.map(Singleton.of),
  )

  def genType(s: Scope): Gen[Type] =
    if s.depth <= 0 then
      if s.structures.isEmpty then genLeafType
      else Gen.frequency1(4 -> genLeafType, 1 -> genStructureRef(s))
    else
      val t = s.copy(depth = s.depth - 1)
      frequency[Type](
        4 -> genLeafType,
        1 -> zip(genType(t), genArray(genType(t), 2)).map(Parameterized.of(_, _)),
        1 -> zip(genType(t), genArray(genAnnotation(t), 1)).map(Annotated.of(_, _)),
        1 -> zip(genType(t), genArray(genTypeParameter(t), 1)).map(Polymorphic.of(_, _)),
        1 -> zip(genType(t), genArray(genTypeParameter(t), 1)).map(Existential.of(_, _)),
        1 -> zip(genType(t), Gen.element1("1", "2")).map(Constant.of(_, _)),
        (if s.structures.isEmpty then 0 else 3) -> genStructureRef(s),
      )

  private def genStructureRef(s: Scope): Gen[Type] =
    if s.structures.isEmpty then genLeafType else Gen.elementUnsafe(s.structures.toList)

  def genTypeParameter(s: Scope): Gen[TypeParameter] =
    val t = s.copy(depth = s.depth - 1)
    for
      id <- Gen.element1("A", "B")
      annots <- genArray(genAnnotation(t), 1)
      tparams <- if s.depth <= 0 then Gen.constant(Array.empty[TypeParameter])
      else genArray(genTypeParameter(t), 1)
      variance <- Gen.elementUnsafe(Variance.values.toList)
      lo <- genType(t)
      hi <- genType(t)
    yield TypeParameter.of(id, annots, tparams, variance, lo, hi)

  def genParameterList(s: Scope): Gen[ParameterList] =
    val genParam =
      for
        name <- Gen.element1("p", "q")
        tpe <- genType(s)
        hasDefault <- Gen.boolean
        modifier <- Gen.elementUnsafe(ParameterModifier.values.toList)
      yield MethodParameter.of(name, tpe, hasDefault, modifier)
    zip(genArray(genParam, 2), Gen.boolean).map(ParameterList.of(_, _))

  def genDefinition(s: Scope): Gen[ClassDefinition] =
    val t = s.copy(depth = s.depth - 1)
    for
      name <- genName
      access <- genAccess
      mods <- genModifiers
      annots <- genArray(genAnnotation(t), 1)
      d <- Gen.frequency1[ClassDefinition](
        3 -> genType(t).map(Val.of(name, access, mods, annots, _)),
        2 -> genType(t).map(Var.of(name, access, mods, annots, _)),
        4 ->
          zip(genArray(genTypeParameter(t), 1), genArray(genParameterList(t), 2), genType(t))
            .map(Def.of(name, access, mods, annots, _, _, _)),
        1 ->
          zip(genArray(genTypeParameter(t), 1), genType(t))
            .map(TypeAlias.of(name, access, mods, annots, _, _)),
        1 ->
          zip(genArray(genTypeParameter(t), 1), genType(t), genType(t))
            .map(TypeDeclaration.of(name, access, mods, annots, _, _, _)),
        1 ->
          zip(genArray(genTypeParameter(t), 1), Gen.elementUnsafe(DefinitionType.values.toList))
            .map(ClassLikeDef.of(name, access, mods, annots, _, _)),
      )
    yield d
    end for
  end genDefinition

  /**
   * Some structures, whose parents may refer to the earlier ones and whose members may refer to
   * any of them, their own included.
   */
  def genStructures(depth: Int): Gen[Vector[Structure]] =
    Gen.int(Range.constant(1, 4)).flatMap { n =>
      val declared = new Array[Array[ClassDefinition]](n)
      val inherited = new Array[Array[ClassDefinition]](n)
      def structures(i: Int, built: Vector[Structure]): Gen[Vector[Structure]] =
        if i == n then Gen.constant(built)
        else
          genArray(genType(Scope(depth, built)), 2).flatMap { parents =>
            val s = Structure.of(Strict(parents), Ref(declared, i), Ref(inherited, i))
            structures(i + 1, built :+ s)
          }
      def members(i: Int, all: Vector[Structure]): Gen[Vector[Structure]] =
        if i == n then Gen.constant(all)
        else
          val s = Scope(depth, all)
          for
            ds <- genArray(genDefinition(s), 6)
            is <- genArray(genDefinition(s), 3)
            _ = declared(i) = ds
            _ = inherited(i) = is
            all <- members(i + 1, all)
          yield all
      structures(0, Vector.empty).flatMap(members(0, _))
    }

  val genClass: Gen[ClassLike] =
    for
      structures <- genStructures(depth = 3)
      s = Scope(2, structures)
      name <- Gen.element1("p.C", "C")
      mods <- genModifiers
      dt <- Gen.elementUnsafe(DefinitionType.values.toList)
      selfType <- genType(s)
      structure <- Gen.elementUnsafe(structures.toList)
      children <- genArray(genType(s), 2)
      tparams <- genArray(genTypeParameter(s), 1)
    yield ClassLike.of(
      name,
      Public.of(),
      mods,
      Array.empty,
      dt,
      Strict(selfType),
      Strict(structure),
      Array.empty,
      children,
      true,
      tparams
    )
end HashingEquivalenceGenerators
