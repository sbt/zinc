/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt.api

import xsbti.api._
import xsbti.UseScope
import sbt.internal.inc.UnitSpec

class NameHashingSpecification extends UnitSpec {

  implicit class NameHashesOpts(nameHashes: Array[NameHash]) {

    def in(s: UseScope): Array[NameHash] =
      nameHashes.filter(_.scope() == s)

    def namesIn(s: UseScope): Set[String] =
      nameHashes.collect {
        case nameHash if nameHash.scope() == s =>
          nameHash.name()
      }(collection.breakOut)

    def forNameIn(s: UseScope, name: String): NameHash =
      nameHashes.find(nameHash => nameHash.scope() == s && nameHash.name() == name).get
  }

  "NameHashing" should "generate correct hashes for sealed classes" in {
    val def1 =
      Def.of("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
    val def2 =
      Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)

    val Ala = Projection.of(emptyType, "Ala")
    val Ola = Projection.of(emptyType, "Ola")

    def createClass(types: Type*) =
      simpleClass("Bar", def1, def2)
        .withModifiers(new Modifiers(false, false, false, true, false, false, false, false))
        .withChildrenOfSealedClass(types.toArray)

    val notSealedClass = simpleClass("Bar", def1)

    val baseClass = createClass()
    val classWithAla = createClass(Ala)
    val classWithAlaAndOla = createClass(Ala, Ola)

    def checkOptimizedNames(a: ClassLike, b: ClassLike, optimizedSealed: Boolean) = {
      val nameHashing = new NameHashing(optimizedSealed)
      val nameHashesA = nameHashing.nameHashes(a)
      val nameHashesB = nameHashing.nameHashes(b)

      if (optimizedSealed) {
        nameHashesA.in(UseScope.Default) shouldEqual nameHashesB.in(UseScope.Default)
        assert(nameHashesA.in(UseScope.PatMatTarget).nonEmpty)
        assert(nameHashesB.in(UseScope.PatMatTarget).nonEmpty)
      } else {
        assert(nameHashesA.in(UseScope.PatMatTarget).isEmpty)
        assert(nameHashesB.in(UseScope.PatMatTarget).isEmpty)
        nameHashesA.in(UseScope.Default) should not equal nameHashesB.in(UseScope.Default)
      }

      if (optimizedSealed) {
        nameHashesA.namesIn(UseScope.PatMatTarget) shouldEqual nameHashesB.namesIn(
          UseScope.PatMatTarget)
        nameHashesA.in(UseScope.PatMatTarget) should not equal nameHashesB.in(UseScope.PatMatTarget)
      }

      HashAPI(a) should not equal HashAPI(b)
    }

    checkOptimizedNames(baseClass, classWithAla, optimizedSealed = true)
    checkOptimizedNames(baseClass, classWithAla, optimizedSealed = false)

    checkOptimizedNames(baseClass, classWithAlaAndOla, optimizedSealed = true)
    checkOptimizedNames(classWithAla, classWithAlaAndOla, optimizedSealed = true)

    assert(new NameHashing(true).nameHashes(notSealedClass).in(UseScope.PatMatTarget).isEmpty)
  }

  /**
   * Very basic test which checks whether a name hash is insensitive to
   * definition order (across the whole compilation unit).
   */
  "NameHashing" should "generate hashes that are insensitive to the definition order when adding a new member" in {
    val nameHashing = new NameHashing(false)
    val def1 =
      Def.of("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
    val def2 =
      Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val classBar1 = simpleClass("Bar", def1)
    val classBar2 = simpleClass("Bar", def1, def2)
    val nameHashes1 = nameHashing.nameHashes(classBar1)
    val nameHashes2 = nameHashing.nameHashes(classBar2)
    assertNameHashEqualForRegularName("Bar", nameHashes1, nameHashes2)
    assertNameHashEqualForRegularName("foo", nameHashes1, nameHashes2)
    nameHashes1 namesIn UseScope.Default should not contain "bar"
    nameHashes2 namesIn UseScope.Default should contain("bar")
  }

  /**
   * Very basic test which checks whether a name hash is insensitive to
   * definition order (across the whole compilation unit).
   */
  it should "generate hashes that are insensitive to the definition order" in {
    val nameHashing = new NameHashing(false)
    val def1 =
      Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val def2 =
      Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
    val classA = simpleClass("Foo", def1, def2)
    val classB = simpleClass("Foo", def2, def1)
    val nameHashes1 = nameHashing.nameHashes(classA)
    val nameHashes2 = nameHashing.nameHashes(classB)
    val def1Hash = HashAPI(def1)
    val def2Hash = HashAPI(def2)
    assert(def1Hash !== def2Hash)
    assert(nameHashes1 === nameHashes2)
    ()
  }

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
  it should "generate hashes that are sensitive to the definition location" in {
    val nameHashing = new NameHashing(false)
    val deff =
      Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val classFoo =
      simpleClassLike(name = "Foo", dt = DefinitionType.ClassDef, structure = simpleStructure(deff))
    val objectFoo =
      simpleClassLike(name = "Foo", dt = DefinitionType.Module, structure = simpleStructure(deff))
    val nameHashes1 = nameHashing.nameHashes(classFoo)
    val nameHashes2 = nameHashing.nameHashes(objectFoo)
    assertNameHashNotEqualForRegularName("bar", nameHashes1, nameHashes2)
  }

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
  it should "generate hashes that account for definitions in parent class" in {
    val parentA = simpleClass("Parent")
    val barMethod =
      Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val parentB = simpleClass("Parent", barMethod)
    val childA = {
      val structure =
        Structure.of(lzy(Array[Type](parentA.structure)), emptyMembers, emptyMembers)
      simpleClassLike("Child", structure)
    }
    val childB = {
      val structure = Structure.of(lzy(Array[Type](parentB.structure)),
                                   emptyMembers,
                                   lzy(Array[ClassDefinition](barMethod)))
      simpleClassLike("Child", structure)
    }
    val parentANameHashes = nameHashesForClass(parentA)
    val parentBNameHashes = nameHashesForClass(parentB)
    Set("Parent") === parentANameHashes.namesIn(UseScope.Default)
    Set("Parent", "bar") === parentBNameHashes.namesIn(UseScope.Default)
    assert(parentANameHashes !== parentBNameHashes)
    val childANameHashes = nameHashesForClass(childA)
    val childBNameHashes = nameHashesForClass(childB)
    assertNameHashEqualForRegularName("Child", childANameHashes, childBNameHashes)
    ()
  }

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
  it should "generate hashes that account for structural types in definition" in {

    /** def foo: { bar: Int } */
    val fooMethod1 = {
      val barMethod1 =
        Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
      Def.of("foo",
             publicAccess,
             defaultModifiers,
             Array.empty,
             Array.empty,
             Array.empty,
             simpleStructure(barMethod1))
    }

    /** def foo: { bar: String } */
    val fooMethod2 = {
      val barMethod2 =
        Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
      Def.of("foo",
             publicAccess,
             defaultModifiers,
             Array.empty,
             Array.empty,
             Array.empty,
             simpleStructure(barMethod2))
    }
    val aClass1 = simpleClass("A", fooMethod1)
    val aClass2 = simpleClass("A", fooMethod2)
    val nameHashes1 = nameHashesForClass(aClass1)
    val nameHashes2 = nameHashesForClass(aClass2)
    // note that `bar` does appear here
    assert(Set("A", "foo", "bar") === nameHashes1.namesIn(UseScope.Default))
    assert(Set("A", "foo", "bar") === nameHashes2.namesIn(UseScope.Default))
    assertNameHashEqualForRegularName("A", nameHashes1, nameHashes2)
    assertNameHashNotEqualForRegularName("foo", nameHashes1, nameHashes2)
    assertNameHashNotEqualForRegularName("bar", nameHashes1, nameHashes2)
  }

  /**
   * Checks properties of merge operation on name hashes:
   *
   *   - names in the result is union of names of arguments
   *   - non-conflicting names have their hashes preserved
   *   - conflicting names have their hashes combined
   */
  it should "merge name hashes" in {
    val nameHashing = new NameHashing(false)
    val def1 =
      Def.of("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
    val def2 =
      Def.of("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val classBar = simpleClass("Bar", def1)
    val objectBar = simpleObject("Bar", def2)
    val nameHashes1 = nameHashing.nameHashes(classBar)
    val nameHashes2 = nameHashing.nameHashes(objectBar)
    val merged = NameHashing.merge(nameHashes1, nameHashes2)
    assert(nameHashes1.namesIn(UseScope.Default) === Set("Bar", "foo"))
    assert(nameHashes2.namesIn(UseScope.Default) === Set("Bar", "bar"))
    assert(merged.namesIn(UseScope.Default) === Set("Bar", "foo", "bar"))
    assertNameHashEqualForRegularName("foo", nameHashes1, merged)
    assertNameHashEqualForRegularName("bar", nameHashes2, merged)
    assertNameHashNotEqualForRegularName("Bar", nameHashes1, merged)
    assertNameHashNotEqualForRegularName("Bar", nameHashes2, merged)
  }

  /**
   * Checks that name hashes are being calculated for top level private classes.
   * A class cannot be top level and private in Java but it can be in Scala (it's package private).
   */
  it should "calcualte name hashes for private top level class" in {
    /* class Foo { def foo: String } */
    val fooDef =
      Def.of("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
    val classFoo =
      simpleClassLike("Foo", simpleStructure(fooDef), access = privateAccess, topLevel = true)
    val nameHashes = nameHashesForClass(classFoo)
    assert(Set("Foo", "foo") === nameHashes.namesIn(UseScope.Default))
  }

  private def assertNameHashEqual(scope: UseScope,
                                  name: String,
                                  nameHashes1: Array[NameHash],
                                  nameHashes2: Array[NameHash]) = {
    val nameHash1 = nameHashes1.forNameIn(scope, name)
    val nameHash2 = nameHashes2.forNameIn(scope, name)
    assert(nameHash1 === nameHash2)
  }

  private def assertNameHashEqualForRegularName(name: String,
                                                nameHashes1: Array[NameHash],
                                                nameHashes2: Array[NameHash]) =
    assertNameHashEqual(UseScope.Default, name, nameHashes1, nameHashes2)

  private def assertNameHashNotEqualForRegularName(
      name: String,
      nameHashes1: Array[NameHash],
      nameHashes2: Array[NameHash]
  ) = {
    val nameHash1 = nameHashes1.forNameIn(UseScope.Default, name)
    val nameHash2 = nameHashes2.forNameIn(UseScope.Default, name)
    assert(nameHash1 !== nameHash2)
  }

  private def nameHashesForClass(cl: ClassLike): Array[NameHash] = {
    val nameHashing = new NameHashing(false)
    nameHashing.nameHashes(cl)
  }

  private def lzy[T](x: T): Lazy[T] = new Lazy[T] { def get: T = x }

  private def simpleStructure(defs: ClassDefinition*) =
    Structure.of(lzy(Array.empty[Type]), lzy(defs.toArray), emptyMembers)

  private def simpleClass(name: String, defs: ClassDefinition*): ClassLike = {
    val structure = simpleStructure(defs: _*)
    simpleClassLike(name, structure)
  }

  private def simpleObject(name: String, defs: ClassDefinition*): ClassLike = {
    val structure = simpleStructure(defs: _*)
    simpleClassLike(name, structure, dt = DefinitionType.Module)
  }

  private def simpleClassLike(name: String,
                              structure: Structure,
                              dt: DefinitionType = DefinitionType.ClassDef,
                              topLevel: Boolean = true,
                              access: Access = publicAccess): ClassLike = {
    ClassLike.of(name,
                 access,
                 defaultModifiers,
                 Array.empty,
                 dt,
                 lzy(emptyType),
                 lzy(structure),
                 Array.empty,
                 Array.empty,
                 topLevel,
                 Array.empty)
  }

  private val emptyType = EmptyType.of()
  private val intTpe = Projection.of(emptyType, "Int")
  private val strTpe = Projection.of(emptyType, "String")
  private val publicAccess = Public.of()
  private val privateAccess = Private.of(Unqualified.of())
  private val defaultModifiers =
    new Modifiers(false, false, false, false, false, false, false, false)
  private val emptyMembers = lzy(Array.empty[ClassDefinition])

}
