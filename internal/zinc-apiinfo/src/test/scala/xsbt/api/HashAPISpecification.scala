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
import sbt.internal.inc.HedgehogSupport.shuffle
import xsbti.api.{ Def as ApiDef, * }
import xsbt.api.ClassLikeHelpers.*

object HashAPISpecification extends Properties:
  override def tests: List[Test] = List(
    example("The HashAPI should detect hash changes in private trait vars", privateVars),
    example("detect hash changes in private trait objects", privateObjects),
    example("not detect hash changes in private trait defs", privateDefs),
    example("not detect hash changes in private type defs", privateTypes),
    example("not detect hash changes in private type alias", privateTypeAlias),
    example("detect hash changes in private trait super accessors defs", superAccessors),
    example("not detect hash changes in childrenOfSealedClass ordering", sealedOrdering),
    example(
      "detect hash changes in the kind, modifiers, access and annotations of a top-level class",
      topLevelVariants
    ),
    property("detect any private trait var", propPrivateVars),
    property("not detect any private trait defs, types or aliases", propPrivateMembers),
    property("not detect any reordering of the children of a sealed class", propSealedOrdering),
  )

  private def privateDef(name: String, tpe: Type): ApiDef =
    ApiDef.of(name, privateAccess, defaultMods, Array.empty, Array.empty, Array.empty, tpe)

  private def privateVars: Result =
    val privateVar = Var.of("x", privateAccess, defaultMods, Array.empty, emptyType)
    assertDifferentPrivateAPI(simpleTrait("Foo", List(privateVar)), simpleTrait("Foo", Nil))

  private def privateObjects: Result =
    val privateObject = simpleClassLikeDef("Bar", dt = DefinitionType.Module, privateAccess)
    assertDifferentPrivateAPI(simpleTrait("Foo", List(privateObject)), simpleTrait("Foo", Nil))

  private def privateDefs: Result =
    val tWithPrivateMember = simpleTrait("Foo", List(privateDef("x", emptyType)))
    val tWithPrivateMember2 = simpleTrait("Foo", List(privateDef("x", strTpe)))
    Result.all(
      List(
        assertSamePrivateAPI(tWithPrivateMember, simpleTrait("Foo", Nil)),
        assertSamePrivateAPI(tWithPrivateMember, tWithPrivateMember2),
      )
    )

  private def privateType(name: String, upper: Type): TypeDeclaration =
    TypeDeclaration.of(name, privateAccess, defaultMods, Array.empty, Array.empty, emptyType, upper)

  private def privateTypes: Result =
    val t1 = simpleTrait("Foo", List(privateType("x", emptyType)))
    val t2 = simpleTrait("Foo", List(privateType("x", intTpe)))
    val t3 = simpleTrait("Foo", List(privateType("x", strTpe)))
    Result.all(
      List(
        assertSamePrivateAPI(t1, simpleTrait("Foo", Nil)),
        assertSamePrivateAPI(t1, t2),
        assertSamePrivateAPI(t2, t3),
      )
    )

  private def privateAlias(name: String, tpe: Type): TypeAlias =
    TypeAlias.of(name, privateAccess, defaultMods, Array.empty, Array.empty, tpe)

  private def privateTypeAlias: Result =
    val t1 = simpleTrait("Foo", List(privateAlias("x", strTpe)))
    val t2 = simpleTrait("Foo", List(privateAlias("x", intTpe)))
    Result.all(List(
      assertSamePrivateAPI(t1, simpleTrait("Foo", Nil)),
      assertSamePrivateAPI(t1, t2)
    ))

  private def superAccessors: Result =
    val superMods = new Modifiers(false, false, false, false, false, false, false, true)
    val superDef =
      ApiDef.of("x", privateAccess, superMods, Array.empty, Array.empty, Array.empty, emptyType)
    assertDifferentPrivateAPI(simpleTrait("Foo", List(superDef)), simpleTrait("Foo", Nil))

  private def sealedOrdering: Result =
    val t = Projection.of(emptyType, "t")
    val f = Projection.of(emptyType, "f")
    val b = simpleTrait("Bool", Nil)
    assertSamePrivateAPI(
      b.withChildrenOfSealedClass(Array(f, t)),
      b.withChildrenOfSealedClass(Array(t, f))
    ) // was "-188164889 did not equal 992093599"

  private def topLevelVariants: Result =
    val c = simpleClass("Foo")
    val finalMods = new Modifiers(false, false, true, false, false, false, false, false)
    val sealedMods = new Modifiers(false, false, false, true, false, false, false, false)
    val abstractMods = new Modifiers(true, false, false, false, false, false, false, false)
    val qualifiedPrivate = Private.of(IdQualifier.of("pkg"))
    val deprecated = Annotation.of(Projection.of(emptyType, "deprecated"), Array.empty)
    val variants = List(
      c,
      c.withModifiers(finalMods),
      c.withModifiers(sealedMods),
      c.withModifiers(abstractMods),
      c.withAccess(qualifiedPrivate),
      c.withAnnotations(Array(deprecated)),
      c.withDefinitionType(DefinitionType.Trait)
    )
    variants.map(HashAPI(_)).distinct.size ==== variants.size

  private val genName: Gen[String] = Gen.string(Gen.lower, Range.linear(1, 6))

  private val genType: Gen[Type] =
    Gen.choice1(
      Gen.element1[Type](emptyType, intTpe, strTpe),
      genName.map(n => Projection.of(emptyType, n)),
    )

  private val genPrivateMember: Gen[ClassDefinition] =
    for
      name <- genName
      tpe <- genType
      member <- Gen.element1[ClassDefinition](
        privateDef(name, tpe),
        privateType(name, tpe),
        privateAlias(name, tpe),
      )
    yield member

  def propPrivateVars: Property =
    for
      name <- genName.forAll
      tpe <- genType.forAll
    yield
      val privateVar = Var.of(name, privateAccess, defaultMods, Array.empty, tpe)
      assertDifferentPrivateAPI(simpleTrait("Foo", List(privateVar)), simpleTrait("Foo", Nil))

  def propPrivateMembers: Property =
    for
      members1 <- Gen.list(genPrivateMember, Range.linear(0, 5)).forAll
      members2 <- Gen.list(genPrivateMember, Range.linear(0, 5)).forAll
    yield assertSamePrivateAPI(simpleTrait("Foo", members1), simpleTrait("Foo", members2))

  def propSealedOrdering: Property =
    for
      children <-
        Gen.list(genName.map(n => Projection.of(emptyType, n): Type), Range.linear(0, 6)).forAll
      shuffled <- shuffle(children).forAll
    yield
      val b = simpleTrait("Sealed", Nil)
      assertSamePrivateAPI(
        b.withChildrenOfSealedClass(children.toArray),
        b.withChildrenOfSealedClass(shuffled.toArray)
      )

  private def assertDifferentPrivateAPI(a: ClassLike, b: ClassLike): Result =
    assertPrivateApi(true, a, b)

  private def assertSamePrivateAPI(a: ClassLike, b: ClassLike): Result =
    assertPrivateApi(false, a, b)

  private def assertPrivateApi(isDifferent: Boolean, a: ClassLike, b: ClassLike): Result =
    def privateAPI(c: ClassLike): Int = HashAPI(_.hashAPI(c), includePrivateDefsInTrait = true)
    def checkOrder(a: ClassLike, b: ClassLike): Result =
      Result.all(
        List(
          // Check the default Hash API doesn't take private methods into account
          (HashAPI(a) ==== HashAPI(b)).log(s"HashAPI($a) != HashAPI($b)"),
          if isDifferent then Result.diff(privateAPI(a), privateAPI(b))(_ != _).log(s"$a vs $b")
          else (privateAPI(a) ==== privateAPI(b)).log(s"$a vs $b"),
        )
      )
    Result.all(List(checkOrder(a, b), checkOrder(b, a)))
end HashAPISpecification
