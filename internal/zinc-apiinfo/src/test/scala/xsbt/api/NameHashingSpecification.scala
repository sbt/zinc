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
import xsbti.UseScope
import xsbt.api.ClassLikeHelpers.*

object NameHashingSpecification extends Properties:
  override def tests: List[Test] = List(
    example("NameHashing should generate correct hashes for sealed classes", sealedClasses),
    example(
      "generate hashes that are insensitive to the definition order when adding a new member",
      addingMember
    ),
    example("generate hashes that are insensitive to the definition order", definitionOrder),
    example("generate hashes that are sensitive to the definition location", definitionLocation),
    example("generate hashes that account for definitions in parent class", parentDefinitions),
    example("generate hashes that account for structural types in definition", structuralTypes),
    example("merge name hashes", mergeNameHashes),
    example("calculate name hashes for private top level class", privateTopLevel),
    property("generate the same hashes for any definition order", propDefinitionOrder),
    property("hash the name of the class and of every member", propNames),
    property("keep the hash of a member when another member is added", propAddingMember),
    property("merge name hashes into the union of their names", propMerge),
  )

  extension (nameHashes: Array[NameHash])
    def in(s: UseScope): List[NameHash] = nameHashes.filter(_.scope() == s).toList

    def namesIn(s: UseScope): Set[String] = nameHashes.filter(_.scope() == s).map(_.name()).toSet

    def forNameIn(s: UseScope, name: String): Option[NameHash] =
      nameHashes.find(nameHash => nameHash.scope() == s && nameHash.name() == name)

  private def publicDef(name: String, tpe: Type): ApiDef =
    ApiDef.of(name, publicAccess, defaultMods, Array.empty, Array.empty, Array.empty, tpe)

  private def nameHashesForClass(cl: ClassLike): Array[NameHash] =
    new NameHashing(false).nameHashes(cl)

  private def sealedClasses: Result =
    val def1 = publicDef("foo", strTpe)
    val def2 = publicDef("bar", intTpe)
    val Ala = Projection.of(emptyType, "Ala")
    val Ola = Projection.of(emptyType, "Ola")
    def createClass(types: Type*) =
      simpleClass("Bar", def1, def2)
        .withModifiers(new Modifiers(false, false, false, true, false, false, false, false))
        .withChildrenOfSealedClass(types.toArray)
    val baseClass = createClass()
    val classWithAla = createClass(Ala)
    val classWithAlaAndOla = createClass(Ala, Ola)

    def checkOptimizedNames(a: ClassLike, b: ClassLike, optimizedSealed: Boolean): Result =
      val nameHashing = new NameHashing(optimizedSealed)
      val nameHashesA = nameHashing.nameHashes(a)
      val nameHashesB = nameHashing.nameHashes(b)
      val scopes =
        if optimizedSealed then
          List(
            nameHashesA.in(UseScope.Default) ==== nameHashesB.in(UseScope.Default),
            Result.assert(nameHashesA.in(UseScope.PatMatTarget).nonEmpty),
            Result.assert(nameHashesB.in(UseScope.PatMatTarget).nonEmpty),
            nameHashesA.namesIn(UseScope.PatMatTarget) ====
              nameHashesB.namesIn(UseScope.PatMatTarget),
            Result.diff(
              nameHashesA.in(UseScope.PatMatTarget),
              nameHashesB.in(UseScope.PatMatTarget)
            )(_ != _),
          )
        else
          List(
            nameHashesA.in(UseScope.PatMatTarget) ==== Nil,
            nameHashesB.in(UseScope.PatMatTarget) ==== Nil,
            Result.diff(nameHashesA.in(UseScope.Default), nameHashesB.in(UseScope.Default))(_ != _),
          )
      Result.all(Result.diff(HashAPI(a), HashAPI(b))(_ != _) :: scopes)
    end checkOptimizedNames

    Result.all(
      List(
        checkOptimizedNames(baseClass, classWithAla, optimizedSealed = true),
        checkOptimizedNames(baseClass, classWithAla, optimizedSealed = false),
        checkOptimizedNames(baseClass, classWithAlaAndOla, optimizedSealed = true),
        checkOptimizedNames(classWithAla, classWithAlaAndOla, optimizedSealed = true),
        new NameHashing(true).nameHashes(simpleClass("Bar", def1)).in(UseScope.PatMatTarget) ====
          Nil,
      )
    )
  end sealedClasses

  /**
   * Very basic test which checks whether a name hash is insensitive to
   * definition order (across the whole compilation unit).
   */
  private def addingMember: Result =
    val def1 = publicDef("foo", strTpe)
    val def2 = publicDef("bar", intTpe)
    val nameHashes1 = nameHashesForClass(simpleClass("Bar", def1))
    val nameHashes2 = nameHashesForClass(simpleClass("Bar", def1, def2))
    Result.all(
      List(
        assertNameHashEqualForRegularName("Bar", nameHashes1, nameHashes2),
        assertNameHashEqualForRegularName("foo", nameHashes1, nameHashes2),
        Result.assert(!nameHashes1.namesIn(UseScope.Default).contains("bar")),
        Result.assert(nameHashes2.namesIn(UseScope.Default).contains("bar")),
      )
    )

  /**
   * Very basic test which checks whether a name hash is insensitive to
   * definition order (across the whole compilation unit).
   */
  private def definitionOrder: Result =
    val def1 = publicDef("bar", intTpe)
    val def2 = publicDef("bar", strTpe)
    val nameHashes1 = nameHashesForClass(simpleClass("Foo", def1, def2))
    val nameHashes2 = nameHashesForClass(simpleClass("Foo", def2, def1))
    Result.all(
      List(
        Result.diff(HashAPI(def1), HashAPI(def2))(_ != _),
        nameHashes1.toList ==== nameHashes2.toList,
      )
    )

  /**
   * Very basic test which asserts that a name hash is sensitive to definition location.
   *
   * For example, if we have:
   * // Foo1.scala
   * class Foo { def xyz: Int = ... }
   * object Foo
   *
   * and:
   * // Foo2.scala
   * class Foo
   * object Foo { def xyz: Int = ... }
   *
   * then hash for `xyz` name should differ in those two cases
   * because method `xyz` was moved from class to an object.
   */
  private def definitionLocation: Result =
    val deff = publicDef("bar", intTpe)
    val classFoo =
      simpleClassLike(name = "Foo", dt = DefinitionType.ClassDef, structure = simpleStructure(deff))
    val objectFoo =
      simpleClassLike(name = "Foo", dt = DefinitionType.Module, structure = simpleStructure(deff))
    assertNameHashNotEqualForRegularName(
      "bar",
      nameHashesForClass(classFoo),
      nameHashesForClass(objectFoo)
    )

  /**
   * Test if members introduced in parent class affect hash of a name
   * of a child class.
   *
   * For example, if we have:
   * // Test1.scala
   * class Parent
   * class Child extends Parent
   *
   * and:
   * // Test2.scala
   * class Parent { def bar: Int = ... }
   * class Child extends Parent
   *
   * then hash for `Child` name should be the same in both
   * cases.
   */
  private def parentDefinitions: Result =
    val parentA = simpleClass("Parent")
    val barMethod = publicDef("bar", intTpe)
    val parentB = simpleClass("Parent", barMethod)
    val childA = simpleClassLike(
      "Child",
      Structure.of(lzy(Array[Type](parentA.structure)), emptyMembers, emptyMembers)
    )
    val childB = simpleClassLike(
      "Child",
      Structure.of(
        lzy(Array[Type](parentB.structure)),
        emptyMembers,
        lzy(Array[ClassDefinition](barMethod))
      )
    )
    val parentANameHashes = nameHashesForClass(parentA)
    val parentBNameHashes = nameHashesForClass(parentB)
    Result.all(
      List(
        parentANameHashes.namesIn(UseScope.Default) ==== Set("Parent"),
        parentBNameHashes.namesIn(UseScope.Default) ==== Set("Parent", "bar"),
        Result.diff(parentANameHashes.toList, parentBNameHashes.toList)(_ != _),
        assertNameHashEqualForRegularName(
          "Child",
          nameHashesForClass(childA),
          nameHashesForClass(childB)
        ),
      )
    )
  end parentDefinitions

  /**
   * Checks if changes to structural types that appear in method signature
   * affect name hash of the method. For example, if we have:
   *
   * // Test1.scala
   * class A {
   * 	def foo: { bar: Int }
   * }
   *
   * // Test2.scala
   * class A {
   *   def foo: { bar: String }
   * }
   *
   * then name hash for "foo" should be different in those two cases.
   */
  private def structuralTypes: Result =

    /** def foo: { bar: Int } */
    val fooMethod1 = publicDef("foo", simpleStructure(publicDef("bar", intTpe)))

    /** def foo: { bar: String } */
    val fooMethod2 = publicDef("foo", simpleStructure(publicDef("bar", strTpe)))
    val nameHashes1 = nameHashesForClass(simpleClass("A", fooMethod1))
    val nameHashes2 = nameHashesForClass(simpleClass("A", fooMethod2))
    Result.all(
      List(
        // note that `bar` does appear here
        nameHashes1.namesIn(UseScope.Default) ==== Set("A", "foo", "bar"),
        nameHashes2.namesIn(UseScope.Default) ==== Set("A", "foo", "bar"),
        assertNameHashEqualForRegularName("A", nameHashes1, nameHashes2),
        assertNameHashNotEqualForRegularName("foo", nameHashes1, nameHashes2),
        assertNameHashNotEqualForRegularName("bar", nameHashes1, nameHashes2),
      )
    )

  /**
   * Checks properties of merge operation on name hashes:
   *
   *   - names in the result is union of names of arguments
   *   - non-conflicting names have their hashes preserved
   *   - conflicting names have their hashes combined
   */
  private def mergeNameHashes: Result =
    val nameHashes1 = nameHashesForClass(simpleClass("Bar", publicDef("foo", strTpe)))
    val nameHashes2 = nameHashesForClass(simpleObject("Bar", publicDef("bar", intTpe)))
    val merged = NameHashing.merge(nameHashes1, nameHashes2)
    Result.all(
      List(
        nameHashes1.namesIn(UseScope.Default) ==== Set("Bar", "foo"),
        nameHashes2.namesIn(UseScope.Default) ==== Set("Bar", "bar"),
        merged.namesIn(UseScope.Default) ==== Set("Bar", "foo", "bar"),
        assertNameHashEqualForRegularName("foo", nameHashes1, merged),
        assertNameHashEqualForRegularName("bar", nameHashes2, merged),
        assertNameHashNotEqualForRegularName("Bar", nameHashes1, merged),
        assertNameHashNotEqualForRegularName("Bar", nameHashes2, merged),
      )
    )

  /**
   * Checks that name hashes are being calculated for top level private classes.
   * A class cannot be top level and private in Java but it can be in Scala (it's package private).
   */
  private def privateTopLevel: Result =
    /* class Foo { def foo: String } */
    val classFoo = simpleClassLike(
      "Foo",
      simpleStructure(publicDef("foo", strTpe)),
      access = privateAccess,
      topLevel = true
    )
    nameHashesForClass(classFoo).namesIn(UseScope.Default) ==== Set("Foo", "foo")

  private val genName: Gen[String] = Gen.string(Gen.lower, Range.linear(1, 6))

  private val genDef: Gen[ApiDef] =
    for
      name <- genName
      tpe <- Gen.element1[Type](emptyType, intTpe, strTpe)
    yield publicDef(name, tpe)

  private def sorted(nameHashes: Array[NameHash]): List[(String, UseScope, Int)] =
    nameHashes.toList.map(nh => (nh.name, nh.scope, nh.hash)).sortBy(nh => (nh._1, nh._2.ordinal))

  def propDefinitionOrder: Property =
    for
      defs <- Gen.list(genDef, Range.linear(0, 8)).forAll
      shuffled <- shuffle(defs).forAll
    yield sorted(nameHashesForClass(simpleClass("Foo", defs*))) ====
      sorted(nameHashesForClass(simpleClass("Foo", shuffled*)))

  def propNames: Property =
    for
      className <- genName.map(_.capitalize).forAll
      defs <- Gen.list(genDef, Range.linear(0, 8)).forAll
    yield nameHashesForClass(simpleClass(className, defs*)).namesIn(UseScope.Default) ====
      defs.map(_.name).toSet + className

  def propAddingMember: Property =
    for
      defs <- Gen.list(genDef, Range.linear(1, 8)).forAll
      added <- genDef.filter(d => !defs.exists(_.name == d.name)).forAll
      at <- Gen.int(Range.linear(0, defs.size)).forAll
    yield
      val before = nameHashesForClass(simpleClass("Foo", defs*))
      val after = nameHashesForClass(simpleClass("Foo", defs.patch(at, List(added), 0)*))
      Result.all(
        ("Foo" :: defs.map(_.name)).distinct.map(assertNameHashEqualForRegularName(
          _,
          before,
          after
        ))
      )

  def propMerge: Property =
    for
      defs1 <- Gen.list(genDef, Range.linear(0, 5)).forAll
      defs2 <- Gen.list(genDef, Range.linear(0, 5)).forAll
    yield
      val nameHashes1 = nameHashesForClass(simpleClass("Bar", defs1*))
      val nameHashes2 = nameHashesForClass(simpleObject("Bar", defs2*))
      val merged = NameHashing.merge(nameHashes1, nameHashes2)
      val onlyIn1 = nameHashes1.namesIn(UseScope.Default) -- nameHashes2.namesIn(UseScope.Default)
      Result.all(
        (merged.namesIn(UseScope.Default) ====
          nameHashes1.namesIn(UseScope.Default) ++ nameHashes2.namesIn(UseScope.Default)) ::
          onlyIn1.toList.map(assertNameHashEqualForRegularName(_, nameHashes1, merged))
      )

  private def assertNameHashEqualForRegularName(
      name: String,
      nameHashes1: Array[NameHash],
      nameHashes2: Array[NameHash]
  ): Result =
    (nameHashes1.forNameIn(UseScope.Default, name) ====
      nameHashes2.forNameIn(UseScope.Default, name))
      .log(s"name: $name")

  private def assertNameHashNotEqualForRegularName(
      name: String,
      nameHashes1: Array[NameHash],
      nameHashes2: Array[NameHash]
  ): Result =
    val nameHash1 = nameHashes1.forNameIn(UseScope.Default, name)
    val nameHash2 = nameHashes2.forNameIn(UseScope.Default, name)
    Result
      .all(
        List(
          Result.assert(nameHash1.isDefined && nameHash2.isDefined),
          Result.diff(nameHash1, nameHash2)(_ != _),
        )
      )
      .log(s"name: $name")
end NameHashingSpecification
