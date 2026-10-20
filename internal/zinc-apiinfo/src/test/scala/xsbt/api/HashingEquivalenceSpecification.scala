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
import xsbti.api._

/**
 * `HashAPI` and `NameHashing` agree with their legacy copies. The generated classes share
 * structures between members, and members refer back to their own structure, as the visited maps
 * that `HashAPI` keeps across a name group make a hash depend on the order it visits them.
 */
object HashingEquivalenceSpecification extends Properties("HashingEquivalence") {
  import HashingEquivalenceGenerators._

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
      }: _*)
    }
}

object HashingEquivalenceGenerators {
  private final class Ref[T](holder: Array[T], i: Int) extends Lazy[T] {
    def get: T = holder(i)
  }

  private final class Strict[T](t: T) extends Lazy[T] {
    def get: T = t
  }

  // Few names, some qualified, so that name groups hold several definitions.
  val genName: Gen[String] =
    Gen.oneOf("a", "b", "Foo", "x.Foo", "y.Foo", "x.a", "apply", "<init>")

  val genQualifier: Gen[Qualifier] =
    Gen.oneOf(Unqualified.of(), ThisQualifier.of(), IdQualifier.of("x"))

  val genAccess: Gen[Access] = Gen.frequency(
    4 -> Gen.const(Public.of()),
    1 -> genQualifier.map(q => Private.of(q): Access),
    1 -> genQualifier.map(q => Protected.of(q): Access)
  )

  val genModifiers: Gen[Modifiers] =
    for {
      bits <- Gen.listOfN(8, Gen.frequency(3 -> false, 1 -> true))
    } yield new Modifiers(bits(0), bits(1), bits(2), bits(3), bits(4), bits(5), bits(6), bits(7))

  def genArray[T: scala.reflect.ClassTag](g: Gen[T], max: Int): Gen[Array[T]] =
    Gen.choose(0, max).flatMap(n => Gen.listOfN(n, g)).map(_.toArray)

  /** The structures a type at this point may refer to. */
  final case class Scope(depth: Int, structures: Vector[Structure])

  def genAnnotation(s: Scope): Gen[Annotation] =
    for {
      base <- genLeafType
      args <- genArray(
        Gen.zip(genName, Gen.oneOf("1", "2")).map { case (n, v) => AnnotationArgument.of(n, v) },
        2
      )
    } yield Annotation.of(base, args)

  val genPath: Gen[Path] =
    genArray(
      Gen.oneOf[PathComponent](Id.of("x"), Id.of("y"), This.of(), Super.of(Path.of(Array()))),
      2
    ).map(Path.of(_))

  val genLeafType: Gen[Type] = Gen.oneOf(
    genName.map(n => Projection.of(EmptyType.of(), n): Type),
    Gen.oneOf("A", "B").map(id => ParameterRef.of(id): Type),
    Gen.const(EmptyType.of(): Type),
    genPath.map(p => Singleton.of(p): Type)
  )

  def genType(s: Scope): Gen[Type] =
    if (s.depth <= 0) {
      if (s.structures.isEmpty) genLeafType
      else Gen.frequency(4 -> genLeafType, 1 -> genStructureRef(s))
    } else {
      val t = s.copy(depth = s.depth - 1)
      Gen.frequency(
        4 -> genLeafType,
        1 -> Gen.zip(genType(t), genArray(genType(t), 2)).map {
          case (b, as) => Parameterized.of(b, as): Type
        },
        1 -> Gen.zip(genType(t), genArray(genAnnotation(t), 1)).map {
          case (b, as) => Annotated.of(b, as): Type
        },
        1 -> Gen.zip(genType(t), genArray(genTypeParameter(t), 1)).map {
          case (b, ps) => Polymorphic.of(b, ps): Type
        },
        1 -> Gen.zip(genType(t), genArray(genTypeParameter(t), 1)).map {
          case (b, ps) => Existential.of(b, ps): Type
        },
        1 -> Gen.zip(genType(t), Gen.oneOf("1", "2")).map {
          case (b, v) => Constant.of(b, v): Type
        },
        (if (s.structures.isEmpty) 0 else 3) -> genStructureRef(s)
      )
    }

  private def genStructureRef(s: Scope): Gen[Type] =
    if (s.structures.isEmpty) genLeafType else Gen.oneOf(s.structures).map(x => x: Type)

  def genTypeParameter(s: Scope): Gen[TypeParameter] = {
    val t = s.copy(depth = s.depth - 1)
    for {
      id <- Gen.oneOf("A", "B")
      annots <- genArray(genAnnotation(t), 1)
      tparams <- if (s.depth <= 0) Gen.const(Array.empty[TypeParameter])
      else genArray(genTypeParameter(t), 1)
      variance <- Gen.oneOf(Variance.values.toSeq)
      lo <- genType(t)
      hi <- genType(t)
    } yield TypeParameter.of(id, annots, tparams, variance, lo, hi)
  }

  def genParameterList(s: Scope): Gen[ParameterList] = {
    val genParam =
      for {
        name <- Gen.oneOf("p", "q")
        tpe <- genType(s)
        hasDefault <- Gen.oneOf(false, true)
        modifier <- Gen.oneOf(ParameterModifier.values.toSeq)
      } yield MethodParameter.of(name, tpe, hasDefault, modifier)
    Gen.zip(genArray(genParam, 2), Gen.oneOf(false, true)).map {
      case (ps, isImplicit) => ParameterList.of(ps, isImplicit)
    }
  }

  def genDefinition(s: Scope): Gen[ClassDefinition] = {
    val t = s.copy(depth = s.depth - 1)
    for {
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
            .map { case (tps, pls, rt) => Def.of(name, access, mods, annots, tps, pls, rt) },
        1 ->
          Gen
            .zip(genArray(genTypeParameter(t), 1), genType(t))
            .map { case (tps, tpe) => TypeAlias.of(name, access, mods, annots, tps, tpe) },
        1 ->
          Gen
            .zip(genArray(genTypeParameter(t), 1), genType(t), genType(t))
            .map {
              case (tps, lo, hi) => TypeDeclaration.of(name, access, mods, annots, tps, lo, hi)
            },
        1 ->
          Gen
            .zip(genArray(genTypeParameter(t), 1), Gen.oneOf(DefinitionType.values.toSeq))
            .map { case (tps, dt) => ClassLikeDef.of(name, access, mods, annots, tps, dt) }
      )
    } yield d
  }

  /**
   * Some structures, whose parents may refer to the earlier ones and whose members may refer to
   * any of them, their own included.
   */
  def genStructures(depth: Int): Gen[Vector[Structure]] =
    Gen.choose(1, 4).flatMap { n =>
      val declared = new Array[Array[ClassDefinition]](n)
      val inherited = new Array[Array[ClassDefinition]](n)
      def structures(i: Int, built: Vector[Structure]): Gen[Vector[Structure]] =
        if (i == n) Gen.const(built)
        else
          genArray(genType(Scope(depth, built)), 2).flatMap { parents =>
            val s = Structure.of(new Strict(parents), new Ref(declared, i), new Ref(inherited, i))
            structures(i + 1, built :+ s)
          }
      def members(i: Int, all: Vector[Structure]): Gen[Vector[Structure]] =
        if (i == n) Gen.const(all)
        else {
          val s = Scope(depth, all)
          for {
            ds <- genArray(genDefinition(s), 6)
            is <- genArray(genDefinition(s), 3)
            _ = { declared(i) = ds; inherited(i) = is }
            all <- members(i + 1, all)
          } yield all
        }
      structures(0, Vector.empty).flatMap(members(0, _))
    }

  val genClass: Gen[ClassLike] =
    for {
      structures <- genStructures(depth = 3)
      s = Scope(2, structures)
      name <- Gen.oneOf("p.C", "C")
      mods <- genModifiers
      dt <- Gen.oneOf(DefinitionType.values.toSeq)
      selfType <- genType(s)
      structure <- Gen.oneOf(structures)
      children <- genArray(genType(s), 2)
      tparams <- genArray(genTypeParameter(s), 1)
    } yield ClassLike.of(
      name,
      Public.of(),
      mods,
      Array.empty,
      dt,
      new Strict(selfType),
      new Strict(structure),
      Array.empty,
      children,
      true,
      tparams
    )
}
