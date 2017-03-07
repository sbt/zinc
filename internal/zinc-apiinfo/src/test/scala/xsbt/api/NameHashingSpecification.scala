package xsbt.api

import xsbti.api._

import sbt.internal.util.UnitSpec

class NameHashingSpecification extends UnitSpec {

  /**
   * Very basic test which checks whether a name hash is insensitive to
   * definition order (across the whole compilation unit).
   */
  "NameHashing" should "generate hashes that are insensitive to the definition order when adding a new member" in {
    val nameHashing = new NameHashing
    val def1 = new Def("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
    val def2 = new Def("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val classBar1 = simpleClass("Bar", def1)
    val classBar2 = simpleClass("Bar", def1, def2)
    val nameHashes1 = nameHashing.nameHashes(classBar1)
    val nameHashes2 = nameHashing.nameHashes(classBar2)
    assertNameHashEqualForRegularName("Bar", nameHashes1, nameHashes2)
    assertNameHashEqualForRegularName("foo", nameHashes1, nameHashes2)
    nameHashes1.regularMembers.map(_.name).toSeq should not contain ("bar")
    nameHashes2.regularMembers.map(_.name).toSeq should contain("bar")
  }

  /**
   * Very basic test which checks whether a name hash is insensitive to
   * definition order (across the whole compilation unit).
   */
  it should "generate hashes that are insensitive to the definition order" in {
    val nameHashing = new NameHashing
    val def1 = new Def("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val def2 = new Def("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
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
    val nameHashing = new NameHashing
    val deff = new Def("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val classFoo = simpleClassLike(name = "Foo", dt = DefinitionType.ClassDef, structure = simpleStructure(deff))
    val objectFoo = simpleClassLike(name = "Foo", dt = DefinitionType.Module, structure = simpleStructure(deff))
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
    val barMethod = new Def("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val parentB = simpleClass("Parent", barMethod)
    val childA = {
      val structure = new Structure(lzy(Array[Type](parentA.structure)), emptyMembers, emptyMembers)
      simpleClassLike("Child", structure)
    }
    val childB = {
      val structure = new Structure(lzy(Array[Type](parentB.structure)), emptyMembers, lzy(Array[ClassDefinition](barMethod)))
      simpleClassLike("Child", structure)
    }
    val parentANameHashes = nameHashesForClass(parentA)
    val parentBNameHashes = nameHashesForClass(parentB)
    Seq("Parent") === parentANameHashes.regularMembers.map(_.name).toSeq
    Seq("Parent", "bar") === parentBNameHashes.regularMembers.map(_.name).toSeq
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
      val barMethod1 = new Def("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
      new Def("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, simpleStructure(barMethod1))
    }
    /** def foo: { bar: String } */
    val fooMethod2 = {
      val barMethod2 = new Def("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
      new Def("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, simpleStructure(barMethod2))
    }
    val aClass1 = simpleClass("A", fooMethod1)
    val aClass2 = simpleClass("A", fooMethod2)
    val nameHashes1 = nameHashesForClass(aClass1)
    val nameHashes2 = nameHashesForClass(aClass2)
    // note that `bar` does appear here
    assert(Seq("A", "foo", "bar") === nameHashes1.regularMembers.map(_.name).toSeq)
    assert(Seq("A", "foo", "bar") === nameHashes2.regularMembers.map(_.name).toSeq)
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
    val nameHashing = new NameHashing
    val def1 = new Def("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
    val def2 = new Def("bar", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, intTpe)
    val classBar = simpleClass("Bar", def1)
    val objectBar = simpleObject("Bar", def2)
    val nameHashes1 = nameHashing.nameHashes(classBar)
    val nameHashes2 = nameHashing.nameHashes(objectBar)
    val merged = NameHashing.merge(nameHashes1, nameHashes2)
    assert(nameHashes1.regularMembers.map(_.name).toSet === Set("Bar", "foo"))
    assert(nameHashes2.regularMembers.map(_.name).toSet === Set("Bar", "bar"))
    assert(merged.regularMembers.map(_.name).toSet === Set("Bar", "foo", "bar"))
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
    val fooDef = new Def("foo", publicAccess, defaultModifiers, Array.empty, Array.empty, Array.empty, strTpe)
    val classFoo = simpleClassLike("Foo", simpleStructure(fooDef), access = privateAccess, topLevel = true)
    val nameHashes = nameHashesForClass(classFoo)
    assert(Seq("Foo", "foo") === nameHashes.regularMembers.map(_.name).toSeq)
  }

  private def assertNameHashEqualForRegularName(name: String, nameHashes1: NameHashes,
    nameHashes2: NameHashes) = {
    val nameHash1 = nameHashForRegularName(nameHashes1, name)
    val nameHash2 = nameHashForRegularName(nameHashes1, name)
    assert(nameHash1 === nameHash2)
  }

  private def assertNameHashNotEqualForRegularName(name: String, nameHashes1: NameHashes, nameHashes2: NameHashes) = {
    val nameHash1 = nameHashForRegularName(nameHashes1, name)
    val nameHash2 = nameHashForRegularName(nameHashes2, name)
    assert(nameHash1 !== nameHash2)
  }

  private def nameHashForRegularName(nameHashes: NameHashes, name: String): NameHash =
    try {
      nameHashes.regularMembers.find(_.name == name).get
    } catch {
      case e: NoSuchElementException => throw new RuntimeException(s"Couldn't find $name in $nameHashes", e)
    }

  private def nameHashesForClass(cl: ClassLike): NameHashes = {
    val nameHashing = new NameHashing
    nameHashing.nameHashes(cl)
  }

  private def lzy[T](x: T): Lazy[T] = new Lazy[T] { def get: T = x }

  private def simpleStructure(defs: ClassDefinition*) =
    new Structure(lzy(Array.empty[Type]), lzy(defs.toArray), emptyMembers)

  private def simpleClass(name: String, defs: ClassDefinition*): ClassLike = {
    val structure = simpleStructure(defs: _*)
    simpleClassLike(name, structure)
  }

  private def simpleObject(name: String, defs: ClassDefinition*): ClassLike = {
    val structure = simpleStructure(defs: _*)
    simpleClassLike(name, structure, dt = DefinitionType.Module)
  }

  private def simpleClassLike(name: String, structure: Structure,
    dt: DefinitionType = DefinitionType.ClassDef, topLevel: Boolean = true, access: Access = publicAccess): ClassLike = {
    new ClassLike(name, access, defaultModifiers, Array.empty, dt, lzy(emptyType),
      lzy(structure), Array.empty, Array.empty, topLevel, Array.empty)
  }

  private val emptyType = new EmptyType
  private val intTpe = new Projection(emptyType, "Int")
  private val strTpe = new Projection(emptyType, "String")
  private val publicAccess = new Public
  private val privateAccess = new Private(new Unqualified)
  private val defaultModifiers = new Modifiers(false, false, false, false, false, false, false, false)
  private val emptyMembers = lzy(Array.empty[ClassDefinition])

}
