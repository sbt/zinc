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

import org.scalacheck.{ Gen, Prop, Properties, Test }
import org.scalacheck.Prop.forAll
import xsbti.UseScope
import xsbti.api.*

/**
 * `HashAPI` and `NameHashing` agree with their legacy copies. The generated classes share
 * structures between members, and members refer back to their own structure, as the visited maps
 * that `HashAPI` keeps across a name group make a hash depend on the order it visits them.
 */
object HashingEquivalenceSpecification extends Properties("HashingEquivalence"):
  import HashingEquivalenceGenerators.*

  override def overrideParameters(p: Test.Parameters): Test.Parameters =
    p.withMinSuccessfulTests(500)

  private def sorted(nameHashes: Array[NameHash]): List[(String, UseScope, Int)] =
    nameHashes.toList.map(nh => (nh.name, nh.scope, nh.hash)).sortBy(nh => (nh._1, nh._2.ordinal))

  property("apiHash") = forAll(genClass)(c => LegacyHashAPI(c) == HashAPI(c))

  property("extraHash") = forAll(genClass) { c =>
    LegacyHashAPI(_.hashAPI(c), includePrivateDefsInTrait = true) ==
      HashAPI(_.hashAPI(c), includePrivateDefsInTrait = true)
  }

  property("nameHashes") = forAll(genClass, Gen.oneOf(false, true)) { (c, optimizedSealed) =>
    sorted(new LegacyNameHashing(optimizedSealed).nameHashes(c)) ==
      sorted(new NameHashing(optimizedSealed).nameHashes(c))
  }

  property("nameHashes with one instance across classes") =
    forAll(Gen.listOfN(3, genClass), Gen.oneOf(false, true)) { (cs, optimizedSealed) =>
      val nameHashing = new NameHashing(optimizedSealed)
      Prop.all(cs.map { c =>
        Prop(
          sorted(new LegacyNameHashing(optimizedSealed).nameHashes(c)) ==
            sorted(nameHashing.nameHashes(c))
        )
      }*)
    }
end HashingEquivalenceSpecification

object HashingEquivalenceGenerators:
  private final class Ref[T](holder: Array[T], i: Int) extends Lazy[T]:
    def get: T = holder(i)

  private final class Strict[T](t: T) extends Lazy[T]:
    def get: T = t

  // Few names, some qualified, so that name groups hold several definitions.
  val genName: Gen[String] =
    Gen.oneOf("a", "b", "Foo", "x.Foo", "y.Foo", "x.a", "apply", "<init>")

  val genQualifier: Gen[Qualifier] =
    Gen.oneOf(Unqualified.of(), ThisQualifier.of(), IdQualifier.of("x"))

  val genAccess: Gen[Access] = Gen.frequency(
    4 -> Gen.const(Public.of()),
    1 -> genQualifier.map(Private.of),
    1 -> genQualifier.map(Protected.of),
  )

  val genModifiers: Gen[Modifiers] =
    for
      bits <- Gen.listOfN(8, Gen.frequency(3 -> false, 1 -> true))
    yield new Modifiers(bits(0), bits(1), bits(2), bits(3), bits(4), bits(5), bits(6), bits(7))

  def genArray[T: scala.reflect.ClassTag](g: Gen[T], max: Int): Gen[Array[T]] =
    Gen.choose(0, max).flatMap(n => Gen.listOfN(n, g)).map(_.toArray)

  /** The structures a type at this point may refer to. */
  final case class Scope(depth: Int, structures: Vector[Structure])

  def genAnnotation(s: Scope): Gen[Annotation] =
    for
      base <- genLeafType
      args <- genArray(Gen.zip(genName, Gen.oneOf("1", "2")).map(AnnotationArgument.of(_, _)), 2)
    yield Annotation.of(base, args)

  val genPath: Gen[Path] =
    genArray(Gen.oneOf(Id.of("x"), Id.of("y"), This.of(), Super.of(Path.of(Array()))), 2)
      .map(cs => Path.of(cs.asInstanceOf[Array[PathComponent]]))

  val genLeafType: Gen[Type] = Gen.oneOf(
    genName.map(Projection.of(EmptyType.of(), _)),
    Gen.oneOf("A", "B").map(ParameterRef.of),
    Gen.const(EmptyType.of()),
    genPath.map(Singleton.of),
  )

  def genType(s: Scope): Gen[Type] =
    if s.depth <= 0 then
      if s.structures.isEmpty then genLeafType
      else Gen.frequency(4 -> genLeafType, 1 -> genStructureRef(s))
    else
      val t = s.copy(depth = s.depth - 1)
      Gen.frequency(
        4 -> genLeafType,
        1 -> Gen.zip(genType(t), genArray(genType(t), 2)).map(Parameterized.of(_, _)),
        1 -> Gen.zip(genType(t), genArray(genAnnotation(t), 1)).map(Annotated.of(_, _)),
        1 -> Gen.zip(genType(t), genArray(genTypeParameter(t), 1)).map(Polymorphic.of(_, _)),
        1 -> Gen.zip(genType(t), genArray(genTypeParameter(t), 1)).map(Existential.of(_, _)),
        1 -> Gen.zip(genType(t), Gen.oneOf("1", "2")).map(Constant.of(_, _)),
        (if s.structures.isEmpty then 0 else 3) -> genStructureRef(s),
      )

  private def genStructureRef(s: Scope): Gen[Type] =
    if s.structures.isEmpty then genLeafType else Gen.oneOf(s.structures)

  def genTypeParameter(s: Scope): Gen[TypeParameter] =
    val t = s.copy(depth = s.depth - 1)
    for
      id <- Gen.oneOf("A", "B")
      annots <- genArray(genAnnotation(t), 1)
      tparams <- if s.depth <= 0 then Gen.const(Array.empty[TypeParameter])
      else genArray(genTypeParameter(t), 1)
      variance <- Gen.oneOf(Variance.values.toSeq)
      lo <- genType(t)
      hi <- genType(t)
    yield TypeParameter.of(id, annots, tparams, variance, lo, hi)

  def genParameterList(s: Scope): Gen[ParameterList] =
    val genParam =
      for
        name <- Gen.oneOf("p", "q")
        tpe <- genType(s)
        hasDefault <- Gen.oneOf(false, true)
        modifier <- Gen.oneOf(ParameterModifier.values.toSeq)
      yield MethodParameter.of(name, tpe, hasDefault, modifier)
    Gen.zip(genArray(genParam, 2), Gen.oneOf(false, true)).map(ParameterList.of(_, _))

  def genDefinition(s: Scope): Gen[ClassDefinition] =
    val t = s.copy(depth = s.depth - 1)
    for
      name <- genName
      access <- genAccess
      mods <- genModifiers
      annots <- genArray(genAnnotation(t), 1)
      d <- Gen.frequency[ClassDefinition](
        3 -> genType(t).map(Val.of(name, access, mods, annots, _)),
        2 -> genType(t).map(Var.of(name, access, mods, annots, _)),
        4 ->
          Gen
            .zip(genArray(genTypeParameter(t), 1), genArray(genParameterList(t), 2), genType(t))
            .map(Def.of(name, access, mods, annots, _, _, _)),
        1 ->
          Gen
            .zip(genArray(genTypeParameter(t), 1), genType(t))
            .map(TypeAlias.of(name, access, mods, annots, _, _)),
        1 ->
          Gen
            .zip(genArray(genTypeParameter(t), 1), genType(t), genType(t))
            .map(TypeDeclaration.of(name, access, mods, annots, _, _, _)),
        1 ->
          Gen
            .zip(genArray(genTypeParameter(t), 1), Gen.oneOf(DefinitionType.values.toSeq))
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
    Gen.choose(1, 4).flatMap { n =>
      val declared = new Array[Array[ClassDefinition]](n)
      val inherited = new Array[Array[ClassDefinition]](n)
      def structures(i: Int, built: Vector[Structure]): Gen[Vector[Structure]] =
        if i == n then Gen.const(built)
        else
          genArray(genType(Scope(depth, built)), 2).flatMap { parents =>
            val s = Structure.of(Strict(parents), Ref(declared, i), Ref(inherited, i))
            structures(i + 1, built :+ s)
          }
      def members(i: Int, all: Vector[Structure]): Gen[Vector[Structure]] =
        if i == n then Gen.const(all)
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
      name <- Gen.oneOf("p.C", "C")
      mods <- genModifiers
      dt <- Gen.oneOf(DefinitionType.values.toSeq)
      selfType <- genType(s)
      structure <- Gen.oneOf(structures)
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
