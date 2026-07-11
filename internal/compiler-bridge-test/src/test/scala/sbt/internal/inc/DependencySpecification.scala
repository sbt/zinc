package sbt
package internal
package inc

import xsbti.TestCallback.ExtractedClassDependencies

class DependencySpecification
    extends UnitSpec
    with CompilingSpecification
    with BridgeProviderTestkit {

  "Dependency phase" should "extract class dependencies from public members" in {
    val classDependencies = extractClassDependenciesPublic
    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance
    assert(memberRef("A") === Set.empty)
    assert(inheritance("A") === Set.empty)
    assert(memberRef("B") === Set("A", "D"))
    assert(inheritance("B") === Set("D"))
    assert(memberRef("C") === Set("A"))
    assert(inheritance("C") === Set.empty)
    assert(memberRef("D") === Set.empty)
    assert(inheritance("D") === Set.empty)
    assert(memberRef("E") === Set.empty)
    assert(inheritance("E") === Set.empty)
    assert(memberRef("F") === Set("A", "B", "D", "E", "G", "C")) // C is the underlying type of MyC
    assert(inheritance("F") === Set("A", "E"))
    // D is a transitive ancestor of H (via the alias G.T[Int] = B extends D[A])
    assert(memberRef("H") === Set("B", "E", "G", "D"))
    // aliases and applied type constructors are expanded so we have inheritance dependency on B
    assert(inheritance("H") === Set("B", "E"))
  }

  it should "extract class dependencies from local members" in {
    val classDependencies = extractClassDependenciesLocal
    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance
    val localInheritance = classDependencies.localInheritance
    assert(memberRef("A") === Set.empty)
    assert(inheritance("A") === Set.empty)
    assert(memberRef("B") === Set.empty)
    assert(inheritance("B") === Set.empty)
    assert(memberRef("C.Inner1") === Set("A"))
    assert(inheritance("C.Inner1") === Set("A"))
    assert(memberRef("D") === Set("B"))
    assert(inheritance("D") === Set.empty)
    assert(localInheritance("D") === Set("B"))
    assert(memberRef("E") === Set("B"))
    assert(inheritance("E") === Set.empty)
    assert(localInheritance("E") === Set("B"))
  }

  it should "extract class dependencies with trait as first parent" in {
    val classDependencies = extractClassDependenciesTraitAsFirstPatent
    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance
    assert(memberRef("A") === Set.empty)
    assert(inheritance("A") === Set.empty)
    assert(memberRef("B") === Set("A"))
    assert(inheritance("B") === Set("A"))
    // verify that memberRef captures the oddity described in documentation of `Relations.inheritance`
    // we are mainly interested whether dependency on A is captured in `memberRef` relation so
    // the invariant that says that memberRef is superset of inheritance relation is preserved
    assert(memberRef("C") === Set("A", "B"))
    assert(inheritance("C") === Set("A", "B"))
    // D extends C: A and C are direct parents (trait-as-first-parent oddity), B is a
    // transitive ancestor now captured in memberRef so the whole classpath requirement is recorded
    assert(memberRef("D") === Set("A", "B", "C"))
    assert(inheritance("D") === Set("A", "C"))
  }

  it should "extract dependencies on the types of inherited members" in {
    val srcA = "class A"
    val srcB = "trait B { val x: A = new A }"
    val srcC = "class C extends B"

    val classDependencies = extractDependenciesFromSrcs(srcA, srcB, srcC)
    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance

    // B declares `x: A`, so it depends on A directly
    assert(memberRef("B") === Set("A"))
    assert(inheritance("B") === Set.empty)
    // C needs A on the classpath for the inherited `x: A`, recorded as memberRef
    assert(memberRef("C") === Set("A", "B"))
    assert(inheritance("C") === Set("B"))
  }

  it should "extract dependencies on transitive ancestors" in {
    val srcA = "trait A"
    val srcB = "trait B extends A"
    val srcC = "class C extends B"

    val classDependencies = extractDependenciesFromSrcs(srcA, srcB, srcC)
    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance

    // the transitive ancestor A (C -> B -> A) is recorded in memberRef
    assert(memberRef("C") === Set("A", "B"))
    assert(inheritance("C") === Set("B"))
  }

  it should "extract binary dependencies on the types of inherited members" in {
    val srcA = "class A"
    val srcB = "trait B { val x: A = new A }"
    val srcC = "class C extends B"

    // C compiles against A/B class files only, yet must record the A edge
    val binaryDependencies =
      extractBinaryDependenciesFromSrcs(List(List(srcA, srcB), List(srcC)))
    val memberRef = binaryDependencies.memberRef

    // exact set: no false edges beyond the field type, parent and superclass
    assert(memberRef("C") === Set("A", "B", "java.lang.Object"))
  }

  // the authoritative shape from the issue: the singleton `A.type` of the mixed-in
  // `create` pins A on C's classpath; the abstract `empty` does not
  it should "extract dependencies through a singleton type of a concrete inherited member" in {
    val srcA = "object A"
    val srcB =
      """|trait B {
         |  def create: A.type = A
         |  def empty: String
         |}""".stripMargin
    val srcC =
      """|object C extends B {
         |  def empty: String = ""
         |}""".stripMargin

    val classDependencies = extractDependenciesFromSrcs(srcA, srcB, srcC)
    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance

    assert(memberRef("B") === Set("A"))
    assert(inheritance("B") === Set.empty)
    assert(memberRef("C") === Set("A", "B"))
    assert(inheritance("C") === Set("B"))
  }

  // the same shape through the classfile/unpickled path, where the
  // symbol/SingleType representation differs (the actual #150 scenario)
  it should "extract binary dependencies through a singleton type of a concrete inherited member" in {
    val srcA = "object A"
    val srcB =
      """|trait B {
         |  def create: A.type = A
         |  def empty: String
         |}""".stripMargin
    val srcC =
      """|object C extends B {
         |  def empty: String = ""
         |}""".stripMargin

    val memberRef =
      extractBinaryDependenciesFromSrcs(List(List(srcA, srcB), List(srcC))).memberRef

    // exact set: the mixed-in singleton, the parent, and library types from `empty`
    assert(
      memberRef("C") === Set(
        "A$",
        "B",
        "java.lang.String",
        "java.lang.Object",
        "scala.Predef$"
      )
    )
  }

  it should "not record a type inherited only through an abstract member" in {
    val srcA = "object A"
    val srcB = "trait B { def foo: A.type }"
    val srcC = "abstract class C extends B"

    val memberRef = extractDependenciesFromSrcs(srcA, srcB, srcC).memberRef
    // `foo` is abstract, so `C` materializes nothing for it and does not need `A`
    assert(memberRef("C") === Set("B"))
  }

  it should "not record a type inherited through a concrete superclass member" in {
    val srcA = "object A"
    val srcD = "class D { def foo: A.type = A }"
    val srcC = "class C extends D"

    val memberRef = extractDependenciesFromSrcs(srcA, srcD, srcC).memberRef
    // `foo` is concrete but its byte code lives in the superclass `D`; `C`
    // inherits it without re-emitting, so `C` does not need `A`
    assert(memberRef("C") === Set("D"))
  }

  it should "not record a type inherited by a trait from another trait" in {
    val srcA = "object A"
    val srcB = "trait B { def foo: A.type = A }"
    val srcC = "trait C extends B"

    val memberRef = extractDependenciesFromSrcs(srcA, srcB, srcC).memberRef
    // a trait defers mixing in to the class that finally extends it, so the
    // trait `C` does not need `A`
    assert(memberRef("C") === Set("B"))
  }

  it should "not record a type inherited through a concrete type member" in {
    val srcA = "class A"
    val srcB = "trait B { type T = A }"
    val srcC = "class C extends B"

    val memberRef = extractDependenciesFromSrcs(srcA, srcB, srcC).memberRef
    // a `type` alias is not materialized into the subclass, so `C` does not need `A`
    assert(memberRef("C") === Set("B"))
  }

  it should "extract class dependencies from macro arguments" in {
    val binaryDependencies = extractBinaryDependenciesFromMacroArgument
    val memberRef = binaryDependencies.memberRef
    memberRef("A") should contain allOf ("B$", "C$")
  }

  it should "extract class dependencies from a refinement" in {
    val srcFoo =
      "object Outer {\n  class Inner { type Xyz }\n\n  type TypeInner = Inner { type Xyz = Int }\n}"
    val srcBar = "object Bar {\n  def bar: Outer.TypeInner = null\n}"
    val classDependencies = extractDependenciesFromSrcs(srcFoo, srcBar)

    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance
    assert(memberRef("Outer") === Set.empty)
    assert(inheritance("Outer") === Set.empty)
    assert(memberRef("Bar") === Set("Outer", "Outer.Inner"))
    assert(inheritance("Bar") === Set.empty)
  }

  it should "extract class dependency on a object correctly" in {
    val srcA =
      """object A {
        |   def foo = { B; () }
        |}""".stripMargin
    val srcB = "object B"

    val classDependencies = extractDependenciesFromSrcs(srcA, srcB)

    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance
    assert(memberRef("A") === Set("B"))
    assert(inheritance("A") === Set.empty)
    assert(memberRef("B") === Set.empty)
    assert(inheritance("B") === Set.empty)
  }

  it should "extract class dependency from 'classOf' literal" in {
    val srcA =
      """object A {
        |   print(classOf[B])
        |}""".stripMargin
    val srcB = "class B"

    val classDependencies = extractDependenciesFromSrcs(srcA, srcB)

    val memberRef = classDependencies.memberRef
    val inheritance = classDependencies.inheritance
    assert(memberRef("A") === Set("B"))
    assert(inheritance("A") === Set.empty)
    assert(memberRef("B") === Set.empty)
    assert(inheritance("B") === Set.empty)
  }

  it should "handle top level import dependencies" in {
    val srcA =
      """
        |package abc
        |object A {
        |  class Inner
        |}
        |class A2""".stripMargin
    val srcB = "import abc.A; import abc.A.Inner; class B"
    val srcC = "import abc.{A, A2}; class C"
    val srcD = "import abc.{A2 => Foo}; class D"
    val srcE = "import abc.A._; class E"
    val srcF = "import abc._; class F"
    val srcG =
      """|package foo {
         |  package bar {
         |    import abc.A
         |    class G
         |  }
         |}
      """.stripMargin
    val srcH = "class H { import abc.A }"

    val deps = extractDependenciesFromSrcs(srcA, srcB, srcC, srcD, srcE, srcF, srcG, srcH).memberRef

    assert(deps("A") === Set.empty)
    assert(deps("B") === Set("abc.A", "abc.A.Inner"))
    assert(deps("C") === Set("abc.A", "abc.A2"))
    assert(deps("D") === Set("abc.A2"))
    assert(deps("E") === Set("abc.A"))
    assert(deps("F") === Set.empty)
    assert(deps("foo.bar.G") === Set("abc.A"))
    assert(deps("H") === Set("abc.A"))
  }

  private def extractClassDependenciesPublic: ExtractedClassDependencies = {
    val srcA = "class A"
    val srcB = "class B extends D[A]"
    val srcC = """|class C {
  	  |  def a: A = null
  	  |}""".stripMargin
    val srcD = "class D[T]"
    val srcE = "trait E[T]"
    val srcF = "trait F extends A with E[D[B]] { self: G.MyC => }"
    val srcG = "object G { type T[x] = B ; type MyC = C }"
    // T is a type constructor [x]B
    // B extends D
    // E verifies the core type gets pulled out
    val srcH = "trait H extends G.T[Int] with (E[Int] @unchecked)"

    val classDependencies =
      extractDependenciesFromSrcs(srcA, srcB, srcC, srcD, srcE, srcF, srcG, srcH)
    classDependencies
  }

  private def extractClassDependenciesLocal: ExtractedClassDependencies = {
    val srcA = "class A"
    val srcB = "class B"
    val srcC = "class C { private class Inner1 extends A }"
    val srcD = "class D { def foo: Unit = { class Inner2 extends B } }"
    val srcE = "class E { def foo: Unit = { new B {} } }"

    val classDependencies =
      extractDependenciesFromSrcs(srcA, srcB, srcC, srcD, srcE)
    classDependencies
  }

  private def extractClassDependenciesTraitAsFirstPatent: ExtractedClassDependencies = {
    val srcA = "class A"
    val srcB = "trait B extends A"
    val srcC = "trait C extends B"
    val srcD = "class D extends C"

    val classDependencies =
      extractDependenciesFromSrcs(srcA, srcB, srcC, srcD)
    classDependencies
  }

  private def extractBinaryDependenciesFromMacroArgument: ExtractedClassDependencies = {
    val srcA = "class A { println(B.printTree(C.foo)) }"
    val srcB = """
  		|import scala.language.experimental.macros
  		|import scala.reflect.macros._
  		|object B {
  		|  def printTree(arg: Any): String = macro printTreeImpl
  		|  def printTreeImpl(c: Context)(arg: c.Expr[Any]): c.Expr[String] = {
  		|    val argStr = arg.tree.toString
  		|    val literalStr = c.universe.Literal(c.universe.Constant(argStr))
  		|    c.Expr[String](literalStr)
  		|  }
  		|}""".stripMargin
    val srcC = "object C { val foo = 1 }"

    val binaryDependencies =
      extractBinaryDependenciesFromSrcs(List(List(srcB, srcC), List(srcA)))
    binaryDependencies
  }

}
