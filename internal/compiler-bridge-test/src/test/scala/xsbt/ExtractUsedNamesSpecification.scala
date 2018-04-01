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
    val (usedNames, _, _) = compilerForTesting.extractUsedNamesFromSrc(src)
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
    val (usedNames, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(srcA, srcB)
    val expectedNames = standardNames ++ Set("a", "c", "A", "B", "C", "D", "b", "X", "BB")
    assert(usedNames("b.X") === expectedNames)
    val expectedSymbols = Set(
      (2, 16, "a.A#"),
      (2, 18, "a.A#C#"),
      (2, 20, "a.A#C#D#"),
      (3, 16, "a.B#"),
      (3, 20, "c.BB#")
    )
    assert(referencedSymbols("b.X").filterNot(isStandardSemanticNames) === expectedSymbols)
    val expectedDefinedSymbols =
      Set((1, 16, "b.X#"), (2, 9, "b.X#foo()."), (3, 9, "b.X#bar()."))
    assert(
      definedSymbols("b.X")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "b.X#`<init>`().") === expectedDefinedSymbols)
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
    val (usedNames, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(srcA, srcB)
    val expectedNames = standardNames ++ Set("A", "a", "B", "=", "Int")
    assert(usedNames("B") === expectedNames)
    val expectedSymbols =
      Set((1, 13, "_empty_.A#"), (1, 18, "_empty_.B#foo(A).(a)"), (1, 20, "_empty_.A#$eq()."))
    assert(referencedSymbols("B").filterNot(isStandardSemanticNames) === expectedSymbols)
    val expectedDefinedSymbols =
      Set((0, 6, "_empty_.B#"), (1, 6, "_empty_.B#foo(A)."), (1, 10, "_empty_.B#foo(A).(a)"))
    assert(
      definedSymbols("B")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.B#`<init>`().") === expectedDefinedSymbols)
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
    val (usedNames, referencedSymbols, definedSymbols) =
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

    val expectedSymbolsAX =
      Set((3, 15, "_empty_.A#T#"), (3, 19, "_empty_.A#T#"), (3, 23, "_empty_.A#X#foo(T).(x)"))
    val expectedSymbolsB = Set((0, 16, "_empty_.A#"), (0, 29, "scala.Int#"))
    val expectedSymbolsC = Set((0, 17, "_empty_.B#"))
    val expectedSymbolsD =
      Set((0, 11, "_empty_.C."), (0, 13, "_empty_.A#X."), (0, 15, "_empty_.A#X#foo(T)."))
    assert(referencedSymbols("A.X").filterNot(isStandardSemanticNames) === expectedSymbolsAX)
    assert(referencedSymbols("B").filterNot(isStandardSemanticNames) === expectedSymbolsB)
    assert(referencedSymbols("C").filterNot(isStandardSemanticNames) === expectedSymbolsC)
    assert(referencedSymbols("D").filterNot(isStandardSemanticNames) === expectedSymbolsD)

    val expectedDefinedSymbolsA =
      Set((0, 15, "_empty_.A#"), (1, 6, "_empty_.A#T#"), (2, 8, "_empty_.A#X."))
    val expectedDefinedSymbolsAX =
      Set((3, 8, "_empty_.A#X#foo(T)."), (3, 12, "_empty_.A#X#foo(T).(x)"))
    val expectedDefinedSymbolsB =
      Set((0, 6, "_empty_.B#"), (0, 25, "_empty_.B#T#"))
    val expectedDefinedSymbolsC = Set((0, 7, "_empty_.C."))
    val expectedDefinedSymbolsD = Set((0, 7, "_empty_.D."))
    assert(
      definedSymbols("A")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.A#`<init>`().") === expectedDefinedSymbolsA)
    assert(
      definedSymbols("A.X")
        .filterNot(_._3 == "_empty_.A#X#`<init>`().") === expectedDefinedSymbolsAX)
    assert(
      definedSymbols("B").filterNot(_._3 == "_empty_.B#`<init>`().") === expectedDefinedSymbolsB)
    assert(
      definedSymbols("C").filterNot(_._3 == "_empty_.C#`<init>`().") === expectedDefinedSymbolsC)
    assert(
      definedSymbols("D").filterNot(_._3 == "_empty_.D#`<init>`().") === expectedDefinedSymbolsD)
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
    val (usedNames, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src1, src2)
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

    val expectedSymbols_lista =
      Set((1, 10, "_empty_.B."), (1, 12, "_empty_.B#lista()."))
    val expectedSymbols_at =
      Set((4, 10, "_empty_.B."), (4, 12, "_empty_.B#at()."))
    val expectedSymbols_as =
      Set((7, 10, "_empty_.B."), (7, 12, "_empty_.B#as()."))
    val expectedSymbols_foo =
      Set((10, 10, "_empty_.B."),
          (10, 12, "_empty_.B#foo(M)."),
          (10, 16, "scala.Predef#$qmark$qmark$qmark()."))
    val expectedSymbols_bar =
      Set((13, 10, "_empty_.B."),
          (13, 12, "_empty_.B#bar(Param)."),
          (13, 16, "scala.Predef#$qmark$qmark$qmark()."))
    assert(
      referencedSymbols("Test_lista")
        .filterNot(isStandardSemanticNames) === expectedSymbols_lista)
    assert(
      referencedSymbols("Test_at")
        .filterNot(isStandardSemanticNames) === expectedSymbols_at)
    assert(
      referencedSymbols("Test_as")
        .filterNot(isStandardSemanticNames) === expectedSymbols_as)
    assert(
      referencedSymbols("Test_foo")
        .filterNot(isStandardSemanticNames) === expectedSymbols_foo)
    assert(
      referencedSymbols("Test_bar")
        .filterNot(isStandardSemanticNames) === expectedSymbols_bar)

    val expectedDefinedSymbols_lista = Set(
      (0, 7, "_empty_.Test_lista."),
      (1, 6, "_empty_.Test_lista#x."),
      (1, 6, "_empty_.Test_lista#x()."),
      (3, 7, "_empty_.Test_at."),
      (6, 7, "_empty_.Test_as."),
      (9, 7, "_empty_.Test_foo."),
      (12, 7, "_empty_.Test_bar.")
    )
    val expectedDefinedSymbols_at =
      Set((4, 6, "_empty_.Test_at#x."), (4, 6, "_empty_.Test_at#x()."))
    val expectedDefinedSymbols_as =
      Set((7, 6, "_empty_.Test_as#x."), (7, 6, "_empty_.Test_as#x()."))
    val expectedDefinedSymbols_foo =
      Set((10, 6, "_empty_.Test_foo#x."), (10, 6, "_empty_.Test_foo#x()."))
    val expectedDefinedSymbols_bar =
      Set((13, 6, "_empty_.Test_bar#x."), (13, 6, "_empty_.Test_bar#x()."))
    val expectedDefinedSymbols_B = Set(
      (11, 7, "_empty_.B#S#"),
      (12, 6, "_empty_.B#lista."),
      (12, 6, "_empty_.B#lista()."),
      (13, 6, "_empty_.B#at."),
      (13, 6, "_empty_.B#at()."),
      (14, 6, "_empty_.B#as."),
      (14, 6, "_empty_.B#as()."),
      (15, 6, "_empty_.B#foo(M)."),
      (15, 10, "_empty_.B#foo(M).(m)"),
      (16, 6, "_empty_.B#bar(Param)."),
      (16, 10, "_empty_.B#bar(Param).[Param]"),
      (16, 29, "_empty_.B#bar(Param).(p)")
    )
    assert(
      definedSymbols("Test_lista")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.Test_lista#`<init>`().") === expectedDefinedSymbols_lista)
    assert(
      definedSymbols("Test_at")
        .filterNot(_._3 == "_empty_.Test_at#`<init>`().") === expectedDefinedSymbols_at)
    assert(
      definedSymbols("Test_as")
        .filterNot(_._3 == "_empty_.Test_as#`<init>`().") === expectedDefinedSymbols_as)
    assert(
      definedSymbols("Test_foo")
        .filterNot(_._3 == "_empty_.Test_foo#`<init>`().") === expectedDefinedSymbols_foo)
    assert(
      definedSymbols("Test_bar")
        .filterNot(_._3 == "_empty_.Test_bar#`<init>`().") === expectedDefinedSymbols_bar)
    assert(
      definedSymbols("B").filterNot(_._3 == "_empty_.B#`<init>`().") === expectedDefinedSymbols_B)

  }

  it should "extract used names from an existential" in {
    val srcFoo =
      """import scala.language.existentials
      |class Foo {
      |  val foo: T forSome { type T <: Double } = ???
      |}
      """.stripMargin
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(srcFoo)
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

    val expectedSymbols =
      Set((0, 13, "scala.language."), (2, 44, "scala.Predef#$qmark$qmark$qmark()."))
    assert(referencedSymbols("Foo").filterNot(isStandardSemanticNames) === expectedSymbols)

    val expectedDefinedSymbols =
      Set((1, 6, "_empty_.Foo#"), (2, 6, "_empty_.Foo#foo."), (2, 6, "_empty_.Foo#foo()."))
    assert(
      definedSymbols("Foo")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.Foo#`<init>`().") === expectedDefinedSymbols)
  }

  it should "extract used names from a refinement" in {
    val srcFoo =
      "object Outer {\n  class Inner { type Xyz }\n\n  type TypeInner = Inner { type Xyz = Int }\n}"
    val srcBar = "object Bar {\n  def bar: Outer.TypeInner = null\n}"
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(srcFoo, srcBar)
    val expectedNames = standardNames ++ Set("Bar", "Outer", "TypeInner", "Inner", "Xyz", "Int")
    assert(usedNames("Bar") === expectedNames)

    val expectedSymbols =
      Set((1, 11, "_empty_.Outer."), (1, 17, "_empty_.Outer#TypeInner#"))
    assert(referencedSymbols("Bar").filterNot(isStandardSemanticNames) === expectedSymbols)

    val expectedDefinedSymbols =
      Set((0, 7, "_empty_.Bar."), (1, 6, "_empty_.Bar#bar()."))
    assert(
      definedSymbols("Bar")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.Bar#`<init>`().") === expectedDefinedSymbols)
  }

  // test for https://github.com/gkossakowski/sbt/issues/3
  it should "extract used names from the same compilation unit" in {
    val src = "class A { def foo: Int = 0; def bar: Int = foo }"
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedNames = standardNames ++ Set("A", "foo", "Int")
    assert(usedNames("A") === expectedNames)
    val expectedSymbols =
      Set((0, 19, "scala.Int#"), (0, 37, "scala.Int#"), (0, 43, "_empty_.A#foo()."))
    assert(referencedSymbols("A").filterNot(isStandardSemanticNames) === expectedSymbols)
    val expectedDefinedSymbols =
      Set((0, 6, "_empty_.A#"), (0, 14, "_empty_.A#foo()."), (0, 32, "_empty_.A#bar()."))
    assert(
      definedSymbols("A")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.A#`<init>`().") === expectedDefinedSymbols)
  }

  // pending test for https://issues.scala-lang.org/browse/SI-7173
  it should "extract names of constants" in pendingUntilFixed {
    val src = "class A { final val foo = 12; def bar: Int = foo }"
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (usedNames, _, _) = compilerForTesting.extractUsedNamesFromSrc(src)
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
    val (usedNames, _, _) = compilerForTesting.extractUsedNamesFromSrc(srcA, srcB)
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

  private def isStandardSemanticNames(src: (Int, Int, String)): Boolean =
    src._3 == "scala.AnyRef#" || src._3 == "scala.Any#" || src._3 == "scala.Nothing#"

}
