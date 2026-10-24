package sbt
package internal
package inc

import hedgehog.{ sbt => _, _ }
import hedgehog.runner._
import xsbti.UseScope
import HedgehogSupport.pendingUntilFixed

object ExtractUsedNamesSpecification
    extends Properties
    with CompilingSpecification
    with BridgeProviderTestkit {
  override def tests: List[Test] = List(
    example("Used names extraction should extract imported name", importedName),
    // test covers https://github.com/gkossakowski/sbt/issues/6
    example("extract names in type tree", typeTree),
    // test for https://github.com/gkossakowski/sbt/issues/5
    example("extract symbolic names", symbolicNames),
    example("extract type names for objects depending on abstract types", abstractTypes),
    // See source-dependencies/types-in-used-names-a for an example where
    // this is required.
    example("extract names in the types of trees", typesOfTrees),
    example("extract used names from an existential", existential),
    example("extract used names from a refinement", refinement),
    // test for https://github.com/gkossakowski/sbt/issues/3
    example("extract used names from the same compilation unit", sameUnit),
    property("extract the names of any members used in the same compilation unit", propSameUnit)
      .withTests(10),
    // pending test for https://github.com/scala/bug/issues/7173
    example("extract names of constants", constants),
    // test for https://github.com/gkossakowski/sbt/issues/4
    // TODO: we should fix it by having special treatment of `selectDynamic` and `applyDynamic` calls
    example("extract names from method calls on Dynamic", pendingUntilFixed(dynamic)),
    // This doesn't work in 2.13.0-RC1
    example("extract sealed classes scope", sealedClassesScope),
  )

  /**
   * Standard names that appear in every compilation unit that has any class
   * definition.
   */
  private val standardNames = Set(
    "scala",
    // The default parent of a class is "AnyRef" which is an alias for "Object"
    "AnyRef",
    "Object",
    "java;lang;Object;init;"
  )

  private def importedName: Result = {
    val src = """package a { class A }
                |package b {
                | import a.{A => A2}
                |}""".stripMargin
    val usedNames = extractUsedNamesFromSrc(src)
    // names used at top level are attributed to the first class defined in a compilation unit

    usedNames("a.A") ==== standardNames ++ Set("a", "A", "A2", "b")
  }

  private def typeTree: Result = {
    val srcA = """|package a {
                  |  class A {
                  |    class C { class D }
                  |  }
                  |  class B[T]
                  |}
                  |package c {
                  |  class BB
                  |}
                  |
                  |""".stripMargin
    val srcB = """|package b {
                  | abstract class X {
                  |     def foo: a.A#C#D
                  |     def bar: a.B[c.BB]
                  |   }
                  |}""".stripMargin
    val usedNames = extractUsedNamesFromSrc(srcA, srcB)
    usedNames("b.X") ==== standardNames ++ Set("a", "c", "A", "B", "C", "D", "b", "X", "BB")
  }

  private def symbolicNames: Result = {
    val srcA = """|class A {
                  |  def `=`: Int = 3
                  |}""".stripMargin
    val srcB = """|class B {
                  |  def foo(a: A) = a.`=`
                  |}""".stripMargin
    val usedNames = extractUsedNamesFromSrc(srcA, srcB)
    usedNames("B") ==== standardNames ++ Set("A", "a", "B", "=", "Int")
  }

  private def abstractTypes: Result = {
    val srcA =
      """abstract class A {
        | type T
        | object X {
        |    def foo(x: T): T = x
        |  }
        |}
      """.stripMargin
    val srcB = "class B extends A { type T = Int }"
    val srcC = "object C extends B"
    val srcD = "object D { C.X.foo(12) }"
    val usedNames = extractUsedNamesFromSrc(srcA, srcB, srcC, srcD)
    // TODO: Find out what's making these types appear in 2.10
    // They don't come from type dependency traverser, but from `addSymbol`
    val versionDependentNames =
      if (scalaVersion.startsWith("2.10")) Set("Nothing", "Any") else Set()
    Result.all(
      List(
        usedNames("A") ==== standardNames ++ Set("A") ++ versionDependentNames,
        usedNames("A.X") ==== standardNames ++ Set("X", "x", "T", "A"),
        usedNames("B") ==== Set("B", "A", "Int", "A;init;", "scala"),
        usedNames("C") ==== Set("B;init;", "C", "B"),
        usedNames("D") ==== standardNames ++ Set("D", "C", "X", "foo", "Int", "T"),
      )
    )
  }

  private def typesOfTrees: Result = {
    val src1 = """|class X0
                  |class X1 extends X0
                  |class Y
                  |class A {
                  |  type T >: X1 <: X0
                  |}
                  |class M
                  |class N
                  |class P0
                  |class P1 extends P0
                  |object B {
                  |  type S = Y
                  |  val lista: List[A] = ???
                  |  val at: A#T = ???
                  |  val as: S = ???
                  |  def foo(m: M): N = ???
                  |  def bar[Param >: P1 <: P0](p: Param): Param = ???
                  |}""".stripMargin
    val src2 = """|object Test_lista {
                  |  val x = B.lista
                  |}
                  |object Test_at {
                  |  val x = B.at
                  |}
                  |object Test_as {
                  |  val x = B.as
                  |}
                  |object Test_foo {
                  |  val x = B.foo(???)
                  |}
                  |object Test_bar {
                  |  val x = B.bar(???)
                  |}
                  |""".stripMargin
    val usedNames = extractUsedNamesFromSrc(src1, src2)
    val nothing = Set("Predef", "???", "Nothing")
    Result.all(
      List(
        usedNames("Test_lista") ====
          standardNames ++ Set("Test_lista", "x", "B", "lista", "List", "A"),
        usedNames("Test_at") ====
          standardNames ++ Set("Test_at", "x", "B", "at", "A", "T", "X0", "X1"),
        usedNames("Test_as") ==== standardNames ++ Set("Test_as", "x", "B", "as", "S", "Y"),
        usedNames("Test_foo") ====
          standardNames ++ Set("Test_foo", "x", "B", "foo", "M", "N") ++ nothing,
        usedNames("Test_bar") ====
          standardNames ++ Set("Test_bar", "x", "B", "bar", "Param", "P1", "P0") ++ nothing,
      )
    )
  }

  private def existential: Result = {
    val srcFoo =
      """import scala.language.existentials
      |class Foo {
      |  val foo: T forSome { type T <: Double } = ???
      |}
      """.stripMargin
    val usedNames = extractUsedNamesFromSrc(srcFoo)
    usedNames("Foo") ==== standardNames ++ Set(
      "Double",
      "Foo",
      "T",
      "foo",
      "scala",
      "language",
      "existentials",
      "Nothing",
      "???",
      "Predef"
    )
  }

  private def refinement: Result = {
    val srcFoo =
      "object Outer {\n  class Inner { type Xyz }\n\n  type TypeInner = Inner { type Xyz = Int }\n}"
    val srcBar = "object Bar {\n  def bar: Outer.TypeInner = null\n}"
    val usedNames = extractUsedNamesFromSrc(srcFoo, srcBar)
    usedNames("Bar") ==== standardNames ++ Set("Bar", "Outer", "TypeInner", "Inner", "Xyz", "Int")
  }

  private def sameUnit: Result = {
    val usedNames = extractUsedNamesFromSrc("class A { def foo: Int = 0; def bar: Int = foo }")
    usedNames("A") ==== standardNames ++ Set("A", "foo", "Int")
  }

  /** Each member returns 0 or the sum of earlier members, which are the only names it uses. */
  def propSameUnit: Property =
    for {
      flags <- Gen.list(Gen.list(Gen.boolean, Range.linear(0, 4)), Range.linear(1, 5)).forAll
    } yield {
      val members = flags.zipWithIndex.map {
        case (fs, i) =>
          val used = fs.take(i).zipWithIndex.collect { case (true, j) => s"m$j" }
          val body = if (used.isEmpty) "0" else used.mkString(" + ")
          (s"def m$i: Int = $body", used)
      }
      val src = members.map(_._1).mkString("class A { ", "; ", " }")
      val used = members.flatMap(_._2).toSet
      val plus = if (members.exists(_._2.size > 1)) Set("+") else Set.empty[String]
      val usedNames = extractUsedNamesFromSrc(src)
      (usedNames("A") ==== standardNames ++ Set("A", "Int") ++ used ++ plus).log(src)
    }

  private def constants: Result = {
    val usedNames = extractUsedNamesFromSrc("class A { final val foo = 12; def bar: Int = foo }")
    usedNames("A") ==== standardNames ++ Set("A", "foo", "Int")
  }

  private def dynamic: Result = {
    val srcA = """|import scala.language.dynamics
                  |class A extends Dynamic {
                  | def selectDynamic(name: String): Int = name.length
                  |}""".stripMargin
    val srcB = "class B { def foo(a: A): Int = a.bla }"
    val usedNames = extractUsedNamesFromSrc(srcA, srcB)
    usedNames("B") ==== standardNames ++ Set("B", "A", "a", "Int", "selectDynamic", "bla")
  }

  private def sealedClassesScope: Result = {
    val sealedClassName = "Sealed"
    val sealedClass =
      s"""package base
        |
        |sealed class $sealedClassName
        |object Usage extends $sealedClassName
        |object Usage2 extends $sealedClassName
      """.stripMargin

    def findPatMatUsages(in: String): Set[String] = {
      val (_, callback) = compileSrcs(List(List(sealedClass, in)))
      val clientNames = callback.usedNamesAndScopes.view.filterKeys(!_.startsWith("base.")).toMap
      clientNames.flatMap {
        case (_, usages) => usages.filter(_.scopes.contains(UseScope.PatMatTarget)).map(_.name)
      }.toSet
    }

    def classWithPatMatOfType(tpe: String) =
      s"""package client
        |import base._
        |
        |class test(a: $tpe) {
        |  a match {
        |   case _ => 1
        |  }
        |}
      """.stripMargin

    def inNestedCase(tpe: String) =
      s"""package client
          |import base._
          |
          |class test(a: Any) {
          |  a match {
          |   case _: $tpe => 1
          |  }
          |}""".stripMargin

    val notUsedInPatternMatch =
      s"""package client
          |import base._
          |
          |class test(a: Any) {
          |  a match {
          |   case _ => 1
          |  }
          |  val aa: $sealedClassName = ???
          |}""".stripMargin

    // findPatMatUsages(classWithPatMatOfType()) shouldEqual Set(sealedClassName)

    Result.all(
      List(
        // Option is sealed
        findPatMatUsages(classWithPatMatOfType(s"Option[$sealedClassName]")) ====
          Set(sealedClassName, "Option"),
        // Seq and Set is not
        findPatMatUsages(classWithPatMatOfType(s"Seq[Set[$sealedClassName]]")) ====
          Set(sealedClassName),
        findPatMatUsages(inNestedCase(sealedClassName)) ==== Set.empty[String],
        findPatMatUsages(notUsedInPatternMatch) ==== Set.empty[String],
      )
    )
  }
}
