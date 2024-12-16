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

import xsbti.api._
import sbt.internal.inc.UnitSpec
import xsbt.api.ClassLikeHelpers._

class HashAPISpecification extends UnitSpec {
  behavior of "The HashAPI specification"

  it should "detect hash changes in private trait vars" in {
    val privateVar = Var.of("x", privateAccess, defaultMods, Array.empty, emptyType)
    val tWithPrivateMember = simpleTrait("Foo", List(privateVar))
    val tWithNothing = simpleTrait("Foo", Nil)
    assertDifferentPrivateAPI(tWithPrivateMember, tWithNothing)
  }

  it should "detect hash changes in private trait objects" in {
    val privateObject = simpleClassLikeDef("Bar", dt = DefinitionType.Module, privateAccess)
    val tWithPrivateMember = simpleTrait("Foo", List(privateObject))
    val tWithNothing = simpleTrait("Foo", Nil)
    assertDifferentPrivateAPI(tWithPrivateMember, tWithNothing)
  }

  it should "not detect hash changes in private trait defs" in {
    val privateDef =
      Def.of("x", privateAccess, defaultMods, Array.empty, Array.empty, Array.empty, emptyType)
    val tWithPrivateMember = simpleTrait("Foo", List(privateDef))
    val tWithNothing = simpleTrait("Foo", Nil)
    assertSamePrivateAPI(tWithPrivateMember, tWithNothing)

    val privateDef2 =
      Def.of("x", privateAccess, defaultMods, Array.empty, Array.empty, Array.empty, strTpe)
    val tWithPrivateMember2 = simpleTrait("Foo", List(privateDef2))
    assertSamePrivateAPI(tWithPrivateMember, tWithPrivateMember2)
  }

  it should "not detect hash changes in private type defs" in {
    val e = emptyType
    val privateType =
      TypeDeclaration.of("x", privateAccess, defaultMods, Array.empty, Array.empty, e, e)
    val tWithPrivateMember = simpleTrait("Foo", List(privateType))
    val tWithNothing = simpleTrait("Foo", Nil)
    assertSamePrivateAPI(tWithPrivateMember, tWithNothing)

    val privateType2 =
      TypeDeclaration.of("x", privateAccess, defaultMods, Array.empty, Array.empty, e, intTpe)
    val tWithPrivateMember2 = simpleTrait("Foo", List(privateType2))
    assertSamePrivateAPI(tWithPrivateMember, tWithPrivateMember2)

    val privateType3 =
      TypeDeclaration.of("x", privateAccess, defaultMods, Array.empty, Array.empty, e, strTpe)
    val tWithPrivateMember3 = simpleTrait("Foo", List(privateType3))
    assertSamePrivateAPI(tWithPrivateMember2, tWithPrivateMember3)
  }

  it should "not detect hash changes in private type alias" in {
    val privateTypeAlias =
      TypeAlias.of("x", privateAccess, defaultMods, Array.empty, Array.empty, strTpe)
    val tWithPrivateMember = simpleTrait("Foo", List(privateTypeAlias))
    val tWithNothing = simpleTrait("Foo", Nil)
    assertSamePrivateAPI(tWithPrivateMember, tWithNothing)

    val privateTypeAlias2 =
      TypeAlias.of("x", privateAccess, defaultMods, Array.empty, Array.empty, intTpe)
    val tWithPrivateMember2 = simpleTrait("Foo", List(privateTypeAlias2))
    assertSamePrivateAPI(tWithPrivateMember, tWithPrivateMember2)
  }

  it should "not detect hash changes in private trait super accessors defs" in {
    val superMods =
      new Modifiers(false, false, false, false, false, false, false, true)
    val privateDef =
      Def.of("x", privateAccess, superMods, Array.empty, Array.empty, Array.empty, emptyType)
    val tWithPrivateMember = simpleTrait("Foo", List(privateDef))
    val tWithNothing = simpleTrait("Foo", Nil)
    assertDifferentPrivateAPI(tWithPrivateMember, tWithNothing)
  }

  it should "not detect hash changes in childrenOfSealedClass ordering" in {
    val t = Projection.of(emptyType, "t")
    val f = Projection.of(emptyType, "f")
    val b = simpleTrait("Bool", Nil)
    val x = b.withChildrenOfSealedClass(Array(f, t))
    val y = b.withChildrenOfSealedClass(Array(t, f))
    assertSamePrivateAPI(x, y) // was "-188164889 did not equal 992093599"
  }

  def assertDifferentPrivateAPI(a: ClassLike, b: ClassLike): Unit = assertPrivateApi(true, a, b)
  def assertSamePrivateAPI(a: ClassLike, b: ClassLike): Unit = assertPrivateApi(false, a, b)
  def assertPrivateApi(isDifferent: Boolean, a: ClassLike, b: ClassLike): Unit = {
    def PrivateAPI(c: ClassLike): Int = HashAPI(_.hashAPI(c), includePrivateDefsInTrait = true)
    def checkOrder(a: ClassLike, b: ClassLike): Unit = {
      // Check the default Hash API doesn't take private methods into account
      assert(HashAPI(a) == HashAPI(b), s"HashAPI(${a}) != HashAPI(${b})")
      if (isDifferent)
        assert(PrivateAPI(a) != PrivateAPI(b), s"PrivateAPI(${a}) == PrivateAPI(${b})")
      else
        assert(PrivateAPI(a) == PrivateAPI(b), s"PrivateAPI(${a}) != PrivateAPI(${b})")
      ()
    }

    checkOrder(a, b)
    checkOrder(b, a)
  }
}
