package xsbt

import sbt.internal.inc.UnitSpec
import xsbti.UseScope

class ExtractUsedNamesSpecification extends UnitSpec {

  "Used names extraction" should "extract imported name" in {
    val src = """package a { class A }
                |package b {
                | import a.{A => A2}
                |}""".stripMargin
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, _) = compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedNames = standardNames ++ Set("a", "A", "A2", "b")
    // names used at top level are attributed to the first class defined in a compilation unit

    assert(usedNames("a.A") === expectedNames)
  }

  // test covers https://github.com/gkossakowski/sbt/issues/6
  it should "extract names in type tree" in {
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
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, positionNames) = compilerForTesting.extractUsedNamesFromSrc(srcA, srcB)
    val expectedNames = standardNames ++ Set("a", "c", "A", "B", "C", "D", "b", "X", "BB")
    assert(usedNames("b.X") === expectedNames)
    val expectedPositionNames = Set((3, 15, "a"),
                                    (3, 17, "a.A"),
                                    (3, 19, "a.A.C"),
                                    (3, 21, "a.A.C.D"),
                                    (4, 15, "a"),
                                    (4, 17, "a.B"),
                                    (4, 19, "c"),
                                    (4, 21, "c.BB"),
                                    (6, 1, "scala.AnyRef"))
    assert(positionNames("b.X") === expectedPositionNames)
  }

  // test for https://github.com/gkossakowski/sbt/issues/5
  it should "extract symbolic names" in {
    val srcA = """|class A {
                  |  def `=`: Int = 3
                  |}""".stripMargin
    val srcB = """|class B {
                  |  def foo(a: A) = a.`=`
                  |}""".stripMargin
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, positionNames) = compilerForTesting.extractUsedNamesFromSrc(srcA, srcB)
    val expectedNames = standardNames ++ Set("A", "a", "B", "=", "Int")
    assert(usedNames("B") === expectedNames)
    val expectedPositionNames =
      Set((2, 14, "A"), (2, 19, "B.a"), (2, 21, "A.$eq"), (3, 2, "scala.AnyRef"))
    assert(positionNames("B") === expectedPositionNames)
  }

  it should "extract type names for objects depending on abstract types" in {
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
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, positionNames) =
      compilerForTesting.extractUsedNamesFromSrc(srcA, srcB, srcC, srcD)
    val scalaVersion = scala.util.Properties.versionNumberString
    // TODO: Find out what's making these types appear in 2.10
    // They don't come from type dependency traverser, but from `addSymbol`
    val versionDependentNames =
      if (scalaVersion.contains("2.10")) Set("Nothing", "Any") else Set()
    val namesA = standardNames ++ Set("A") ++ versionDependentNames
    val namesAX = standardNames ++ Set("X", "x", "T", "A")
    val namesB = Set("B", "A", "Int", "A;init;", "scala")
    val namesC = Set("B;init;", "C", "B")
    val namesD = standardNames ++ Set("D", "C", "X", "foo", "Int", "T")
    assert(usedNames("A") === namesA)
    assert(usedNames("A.X") === namesAX)
    assert(usedNames("B") === namesB)
    assert(usedNames("C") === namesC)
    assert(usedNames("D") === namesD)
    val expectedPositionNamesA = Set((7, 6, "scala.AnyRef"))
    val expectedPositionNamesAX =
      Set((4, 16, "A.T"), (4, 20, "A.T"), (4, 24, "A.X.x"), (6, 1, "scala.AnyRef"))
    val expectedPositionNamesB = Set((1, 17, "A"), (1, 30, "scala.Int"))
    val expectedPositionNamesC = Set((1, 18, "B"))
    val expectedPositionNamesD =
      Set((1, 12, "C"), (1, 14, "A.X"), (1, 16, "A.X.foo"), (1, 25, "scala.AnyRef"))
    assert(positionNames("A") === expectedPositionNamesA)
    assert(positionNames("A.X") === expectedPositionNamesAX)
    assert(positionNames("B") === expectedPositionNamesB)
    assert(positionNames("C") === expectedPositionNamesC)
    assert(positionNames("D") === expectedPositionNamesD)
  }

  // See source-dependencies/types-in-used-names-a for an example where
  // this is required.
  it should "extract names in the types of trees" in {
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
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, positionNames) = compilerForTesting.extractUsedNamesFromSrc(src1, src2)
    val expectedNames_lista = standardNames ++ Set("Test_lista", "x", "B", "lista", "List", "A")
    val expectedNames_at = standardNames ++ Set("Test_at", "x", "B", "at", "A", "T", "X0", "X1")
    val expectedNames_as = standardNames ++ Set("Test_as", "x", "B", "as", "S", "Y")
    val expectedNames_foo = standardNames ++ Set("Test_foo",
                                                 "x",
                                                 "B",
                                                 "foo",
                                                 "M",
                                                 "N",
                                                 "Predef",
                                                 "???",
                                                 "Nothing")
    val expectedNames_bar = standardNames ++ Set("Test_bar",
                                                 "x",
                                                 "B",
                                                 "bar",
                                                 "Param",
                                                 "P1",
                                                 "P0",
                                                 "Predef",
                                                 "???",
                                                 "Nothing")
    assert(usedNames("Test_lista") === expectedNames_lista)
    assert(usedNames("Test_at") === expectedNames_at)
    assert(usedNames("Test_as") === expectedNames_as)
    assert(usedNames("Test_foo") === expectedNames_foo)
    assert(usedNames("Test_bar") === expectedNames_bar)
    val expectedPositionNames_lista =
      Set((2, 7, "Test_lista.x"), (2, 11, "B"), (2, 13, "B.lista"), (4, 1, "scala.AnyRef"))
    val expectedPositionNames_at =
      Set((5, 7, "Test_at.x"), (5, 11, "B"), (5, 13, "B.at"), (7, 1, "scala.AnyRef"))
    val expectedPositionNames_as =
      Set((8, 7, "Test_as.x"), (8, 11, "B"), (8, 13, "B.as"), (10, 1, "scala.AnyRef"))
    val expectedPositionNames_foo =
      Set((11, 7, "Test_foo.x"),
          (11, 11, "B"),
          (11, 13, "B.foo"),
          (11, 17, "scala.Predef.$qmark$qmark$qmark"),
          (13, 1, "scala.AnyRef"))
    val expectedPositionNames_bar =
      Set((14, 7, "Test_bar.x"),
          (14, 11, "B"),
          (14, 13, "B.bar"),
          (14, 17, "scala.Predef.$qmark$qmark$qmark"),
          (15, 2, "scala.AnyRef"))
    assert(positionNames("Test_lista") === expectedPositionNames_lista)
    assert(positionNames("Test_at") === expectedPositionNames_at)
    assert(positionNames("Test_as") === expectedPositionNames_as)
    assert(positionNames("Test_foo") === expectedPositionNames_foo)
    assert(positionNames("Test_bar") === expectedPositionNames_bar)
  }

  it should "extract used names from an existential" in {
    val srcFoo =
      """import scala.language.existentials
      |class Foo {
      |  val foo: T forSome { type T <: Double } = ???
      |}
      """.stripMargin
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, positionNames) = compilerForTesting.extractUsedNamesFromSrc(srcFoo)
    val expectedNames = standardNames ++ Seq("Double",
                                             "Foo",
                                             "T",
                                             "foo",
                                             "scala",
                                             "language",
                                             "existentials",
                                             "Nothing",
                                             "???",
                                             "Predef")
    assert(usedNames("Foo") === expectedNames)
  }

  it should "extract used names from a refinement" in {
    val srcFoo =
      "object Outer {\n  class Inner { type Xyz }\n\n  type TypeInner = Inner { type Xyz = Int }\n}"
    val srcBar = "object Bar {\n  def bar: Outer.TypeInner = null\n}"
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, positionNames) = compilerForTesting.extractUsedNamesFromSrc(srcFoo, srcBar)
    val expectedNames = standardNames ++ Set("Bar", "Outer", "TypeInner", "Inner", "Xyz", "Int")
    assert(usedNames("Bar") === expectedNames)
    val expectedPositionNames =
      Set((2, 12, "Outer"), (2, 18, "Outer.TypeInner"), (3, 2, "scala.AnyRef"))
    assert(positionNames("Bar") === expectedPositionNames)

  }

  // test for https://github.com/gkossakowski/sbt/issues/3
  it should "extract used names from the same compilation unit" in {
    val src = "class A { def foo: Int = 0; def bar: Int = foo }"
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, positionNames) = compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedNames = standardNames ++ Set("A", "foo", "Int")
    assert(usedNames("A") === expectedNames)
    val expectedPositionNames =
      Set((1, 20, "scala.Int"), (1, 38, "scala.Int"), (1, 44, "A.foo"), (1, 49, "scala.AnyRef"))
    assert(positionNames("A") === expectedPositionNames)
  }

  // pending test for https://issues.scala-lang.org/browse/SI-7173
  it should "extract names of constants" in pendingUntilFixed {
    val src = "class A { final val foo = 12; def bar: Int = foo }"
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, _) = compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedNames = standardNames ++ Set("A", "foo", "Int")
    assert(usedNames === expectedNames)
    ()
  }

  // test for https://github.com/gkossakowski/sbt/issues/4
  // TODO: we should fix it by having special treatment of `selectDynamic` and `applyDynamic` calls
  it should "extract names from method calls on Dynamic" in pendingUntilFixed {
    val srcA = """|import scala.language.dynamics
                  |class A extends Dynamic {
                  | def selectDynamic(name: String): Int = name.length
                  |}""".stripMargin
    val srcB = "class B { def foo(a: A): Int = a.bla }"
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, _) = compilerForTesting.extractUsedNamesFromSrc(srcA, srcB)
    val expectedNames = standardNames ++ Set("B", "A", "a", "Int", "selectDynamic", "bla")
    assert(usedNames === expectedNames)
    ()
  }

  it should "extract sealed classes scope" in {
    val sealedClassName = "Sealed"
    val sealedClass =
      s"""package base
        |
        |sealed class $sealedClassName
        |object Usage extends $sealedClassName
        |object Usage2 extends $sealedClassName
      """.stripMargin

    def findPatMatUsages(in: String): Set[String] = {
      val compilerForTesting = new ScalaCompilerForUnitTesting
      val (_, callback) =
        compilerForTesting.compileSrcs(List(List(sealedClass, in)), reuseCompilerInstance = false)
      val clientNames = callback.usedNamesAndScopes.filterKeys(!_.startsWith("base."))

      val names: Set[String] = clientNames.flatMap {
        case (_, usages) =>
          usages.filter(_.scopes.contains(UseScope.PatMatTarget)).map(_.name)
      }(collection.breakOut)

      names
    }

    def classWithPatMatOfType(tpe: String = sealedClassName) =
      s"""package client
        |import base._
        |
        |class test(a: $tpe) {
        |  a match {
        |   case _ => 1
        |  }
        |}
      """.stripMargin

    findPatMatUsages(classWithPatMatOfType()) shouldEqual Set(sealedClassName)
    // Option is sealed
    findPatMatUsages(classWithPatMatOfType(s"Option[$sealedClassName]")) shouldEqual Set(
      sealedClassName,
      "Option")
    // Seq and Set is not
    findPatMatUsages(classWithPatMatOfType(s"Seq[Set[$sealedClassName]]")) shouldEqual Set(
      sealedClassName)

    def inNestedCase(tpe: String) =
      s"""package client
          |import base._
          |
          |class test(a: Any) {
          |  a match {
          |   case _: $tpe => 1
          |  }
          |}""".stripMargin

    findPatMatUsages(inNestedCase(sealedClassName)) shouldEqual Set()

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

    findPatMatUsages(notUsedInPatternMatch) shouldEqual Set()
  }

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

}
