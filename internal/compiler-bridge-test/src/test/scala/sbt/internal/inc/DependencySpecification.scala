package sbt
package internal
package inc

import hedgehog.{ sbt => _, _ }
import hedgehog.runner._
import xsbti.NameKind
import xsbti.TestCallback.ExtractedClassDependencies
import xsbti.api.DependencyContext.{ DependencyByInheritance, DependencyByMacroExpansion }

object DependencySpecification
    extends Properties
    with CompilingSpecification
    with BridgeProviderTestkit {
  override def tests: List[Test] = List(
    example(
      "Dependency phase should extract class dependencies from public members",
      publicMembers
    ),
    example("extract class dependencies from local members", localMembers),
    example("extract class dependencies with trait as first parent", traitAsFirstParent),
    example("extract class dependencies from macro arguments", macroArguments),
    example("extract class dependencies from macro type arguments", macroTypeArguments),
    example("extract class dependencies from a refinement", refinement),
    example("not record compound types in members as inheritance", compoundTypes),
    example("extract class dependency on a object correctly", objectDependency),
    example("extract class dependency from 'classOf' literal", classOfLiteral),
    example("handle top level import dependencies", topLevelImports),
    example("qualify each endpoint of a class dependency with its namespace", namespaces),
    property("extract the parent of every class in any inheritance chain", propInheritanceChain)
      .withTests(10),
  )

  private def checkDeps(deps: ExtractedClassDependencies)(
      expected: (String, Set[String], Set[String])*
  ): Result =
    Result.all(expected.toList.flatMap {
      case (name, refs, parents) =>
        List(
          (deps.memberRef(name) ==== refs).log(s"memberRef($name)"),
          (deps.inheritance(name) ==== parents).log(s"inheritance($name)"),
        )
    })

  private def publicMembers: Result =
    checkDeps(extractClassDependenciesPublic)(
      ("A", Set.empty, Set.empty),
      ("B", Set("A", "D"), Set("D")),
      ("C", Set("A"), Set.empty),
      ("D", Set.empty, Set.empty),
      ("E", Set.empty, Set.empty),
      ("F", Set("A", "B", "D", "E", "G", "C"), Set("A", "E")), // C is the underlying type of MyC
      // aliases and applied type constructors are expanded so we have inheritance dependency on B
      ("H", Set("B", "E", "G"), Set("B", "E")),
    )

  private def localMembers: Result = {
    val classDependencies = extractClassDependenciesLocal
    val localInheritance = classDependencies.localInheritance
    Result.all(
      List(
        checkDeps(classDependencies)(
          ("A", Set.empty, Set.empty),
          ("B", Set.empty, Set.empty),
          ("C.Inner1", Set("A"), Set("A")),
          ("D", Set("B"), Set.empty),
          ("E", Set("B"), Set.empty),
        ),
        localInheritance("D") ==== Set("B"),
        localInheritance("E") ==== Set("B"),
      )
    )
  }

  private def traitAsFirstParent: Result =
    checkDeps(extractClassDependenciesTraitAsFirstPatent)(
      ("A", Set.empty, Set.empty),
      ("B", Set("A"), Set("A")),
      // verify that memberRef captures the oddity described in documentation of `Relations.inheritance`
      // we are mainly interested whether dependency on A is captured in `memberRef` relation so
      // the invariant that says that memberRef is superset of inheritance relation is preserved
      ("C", Set("A", "B"), Set("A", "B")),
      // same as above but indirect (C -> B -> A), note that only A is visible here
      ("D", Set("A", "C"), Set("A", "C")),
    )

  private def macroArguments: Result = {
    val memberRef = extractBinaryDependenciesFromMacroArgument.memberRef
    Result.diff(memberRef("A"), Set("B$", "C$"))((a, b) => b.subsetOf(a))
  }

  private def macroTypeArguments: Result =
    Result.diff(
      extractMacroExpansionDependenciesFromTypeArguments,
      Set(("A", "App"), ("A", "App2"), ("B", "App3"))
    )((a, b) => b.subsetOf(a))

  private def refinement: Result = {
    val srcFoo =
      "object Outer {\n  class Inner { type Xyz }\n\n  type TypeInner = Inner { type Xyz = Int }\n}"
    val srcBar = "object Bar {\n  def bar: Outer.TypeInner = null\n}"
    checkDeps(extractDependenciesFromSrcs(srcFoo, srcBar))(
      ("Outer", Set.empty, Set.empty),
      ("Bar", Set("Outer", "Outer.Inner"), Set.empty),
    )
  }

  private def compoundTypes: Result = {
    val srcA =
      """trait A[T <: B with C] { self: B with C =>
        |  def result: B with C = ???
        |  def parameter(value: B with C): Unit = ()
        |  def generic[U <: B with C]: Unit = ()
        |  def cast(value: AnyRef) = value.asInstanceOf[B with C]
        |  type Alias = B with C
        |  type Bound <: B with C
        |  type EmptyRefined = B {}
        |  type Refined = B { type X = C }
        |  type Nested = Box[B with C]
        |  type Existential = (Box[T] forSome { type T <: B }) with C
        |  type Annotated = (B @unchecked) with C
        |}""".stripMargin
    checkDeps(extractDependenciesFromSrcs(srcA, "trait B", "trait C", "trait Box[T]"))(
      ("A", Set("B", "C", "Box"), Set.empty)
    )
  }

  private def objectDependency: Result = {
    val srcA =
      """object A {
        |   def foo = { B; () }
        |}""".stripMargin
    checkDeps(extractDependenciesFromSrcs(srcA, "object B"))(
      ("A", Set("B"), Set.empty),
      ("B", Set.empty, Set.empty),
    )
  }

  private def classOfLiteral: Result = {
    val srcA =
      """object A {
        |   print(classOf[B])
        |}""".stripMargin
    checkDeps(extractDependenciesFromSrcs(srcA, "class B"))(
      ("A", Set("B"), Set.empty),
      ("B", Set.empty, Set.empty),
    )
  }

  private def topLevelImports: Result = {
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
    val d = extractDependenciesFromSrcs(srcA, srcB, srcC, srcD, srcE, srcF, srcG, srcH).memberRef
    Result.all(
      List(
        d("A") ==== Set.empty,
        d("B") ==== Set("abc.A", "abc.A.Inner"),
        d("C") ==== Set("abc.A", "abc.A2"),
        d("D") ==== Set("abc.A2"),
        d("E") ==== Set("abc.A"),
        d("F") ==== Set.empty,
        d("foo.bar.G") ==== Set("abc.A"),
        d("H") ==== Set("abc.A"),
      )
    )
  }

  private def namespaces: Result = {
    val (_, callback) = compileSrcs("trait A", "trait B\nobject B extends A", "class C extends B")
    val inheritance = callback.classRefDependencies.collect {
      case (on, from, DependencyByInheritance) => (on.name, from.name, from.kind)
    }.toSet
    // `object B extends A` and `trait B extends A` are the same edge in the relations
    inheritance ==== Set(("A", "B", NameKind.Term), ("B", "C", NameKind.Type))
  }

  def propInheritanceChain: Property =
    for {
      kinds <- Gen.list(Gen.element1("class", "abstract class"), Range.linear(1, 5)).forAll
    } yield {
      val names = kinds.indices.map(i => s"C$i").toList
      val srcs = kinds.zip(names).zipWithIndex.map {
        case ((kind, name), 0) => s"$kind $name"
        case ((kind, name), i) => s"$kind $name extends ${names(i - 1)}"
      }
      val d = extractDependenciesFromSrcs(srcs: _*)
      Result
        .all(names.zipWithIndex.map {
          case (name, i) =>
            val parent = if (i == 0) Set.empty[String] else Set(names(i - 1))
            (d.inheritance(name) ==== parent).log(s"inheritance($name)")
        })
        .log(srcs.mkString("\n"))
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

  private def extractMacroExpansionDependenciesFromTypeArguments: Set[(String, String)] = {
    val srcMacros =
      """import scala.language.experimental.macros
        |import scala.reflect.macros.blackbox
        |object Macros {
        |  def foo[T]: Unit = macro impl[T]
        |  def bar[T](x: Int)(implicit d: DummyImplicit): Unit = macro implBar[T]
        |  def impl[T: c.WeakTypeTag](c: blackbox.Context): c.Tree = { import c.universe._; q"()" }
        |  def implBar[T: c.WeakTypeTag](c: blackbox.Context)(x: c.Tree)(d: c.Tree): c.Tree = impl[T](c)
        |}
        |class A
        |class B""".stripMargin
    val srcApp =
      """class App { def x = Macros.foo[A] }
        |class App2 { def x = Macros.foo[A] }
        |class App3 { def x = Macros.bar[B](1) }""".stripMargin
    val (_, testCallback) = compileSrcs(List(List(srcMacros), List(srcApp)))
    testCallback.binaryDependencies.toList.collect {
      case (_, on, from, DependencyByMacroExpansion) => (on, from)
    }.toSet
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
