package xsbt

import sbt.internal.inc.UnitSpec

class ExtractUsedNamesNamingConventionSpecification extends UnitSpec {

  "Variable declarations and definitions" should "represented by multiple symbols" in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """abstract class C(val xp: Int) {
                |  val xm: Int = ???
                |  val xam: Int
                |  private[this] val xlm: Int = ???
                |  def m = {
                |    val xl: Int = ???
                |    type S = { val xs: Int }
                |    type E = xe.type forSome { val xe: AnyRef }
                |  }
                |}""".stripMargin
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedReferencedSymbols = Set(
      (0, 25, "scala.Int#"),
      (1, 10, "scala.Int#"),
      (1, 16, "scala.Predef#$qmark$qmark$qmark()."),
      (2, 11, "scala.Int#"),
      (3, 25, "scala.Int#"),
      (3, 31, "scala.Predef#$qmark$qmark$qmark()."),
      (5, 12, "scala.Int#"),
      (5, 18, "scala.Predef#$qmark$qmark$qmark().")
    )
    assert(
      referencedSymbols("C")
        .filterNot(isStandardNames)
        === expectedReferencedSymbols)
    val expectedDefinedSymbols = Set(
      (0, 15, "_empty_.C#"),
      (0, 21, "_empty_.C#xp."),
      (0, 21, "_empty_.C#xp()."),
      (0, 21, "_empty_.C#`<init>`(Int).(xp)"),
      (1, 6, "_empty_.C#xm."),
      (1, 6, "_empty_.C#xm()."),
      (2, 6, "_empty_.C#xam()."),
      (3, 20, "_empty_.C#xlm."),
      (4, 6, "_empty_.C#m().")
    )
    assert(
      definedSymbols("C")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.C#`<init>`(Int).") === expectedDefinedSymbols)
  }

  "Pattern variables" should "represented differently depending on where they are defined" in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """class C {
                |  ??? match { case List(x) => x }
                |  val List(xval) = ???
                |  var List(xvar) = ???
                |}""".stripMargin
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, _, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedDefinedSymbols = Set(
      (0, 6, "_empty_.C#"),
      (2, 10, "_empty_.C#xval."),
      (2, 10, "_empty_.C#xval()."),
      (3, 10, "_empty_.C#xvar."),
      (3, 10, "_empty_.C#xvar()."),
      (3, 10, "_empty_.C#xvar_$eq(Nothing).") // TODO: _empty_.C#xvar_=(Nothing).
    )
    assert(
      definedSymbols("C")
      // `_empty_.C#xvar_$eq(Nothing).(x$1)` occurred after scala 2.12
        .filterNot(_._3 == "_empty_.C#xvar_$eq(Nothing).(x$1)")
        // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.C#`<init>`().") === expectedDefinedSymbols)

  }

  "Type declarations and type aliases" should "represented with TYPE symbols" in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """class C {
                |  type T1 <: Hi
                |  type T2 >: Lo
                |  type T = Int
                |}
                |class Hi {}
                |class Lo {}""".stripMargin

    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedReferencedSymbols = Set(
      (1, 13, "_empty_.Hi#"),
      (2, 13, "_empty_.Lo#"),
      (3, 11, "scala.Int#")
    )
    assert(referencedSymbols("C").filterNot(isStandardNames) === expectedReferencedSymbols)
    val expectedDefinedSymbols = Set(
      (0, 6, "_empty_.C#"),
      (1, 7, "_empty_.C#T1#"),
      (2, 7, "_empty_.C#T2#"),
      (3, 7, "_empty_.C#T#"),
      (5, 6, "_empty_.Hi#"),
      (6, 6, "_empty_.Lo#")
    )
    assert(
      definedSymbols("C")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.C#`<init>`().") === expectedDefinedSymbols)
  }

  "Type variables" should "are represented with TYPE symbols" in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """class C {
                |  ??? match { case _: List[t] =>}
                |}""".stripMargin

    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, _, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedDefinedSymbols = Set(
      (0, 6, "_empty_.C#")
    )
    assert(
      definedSymbols("C").filterNot(_._3 == "_empty_.C#`<init>`().") === expectedDefinedSymbols)
  }

  "Self parameters" should "represented with SELF_PARAMETER symbols" in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """class C1 {
                |  self1 =>
                |}
                |
                |class C2 {
                |  self2: T =>
                |}""".stripMargin

    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, _, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedDefinedSymbols1 = Set(
      (0, 6, "_empty_.C1#"),
      (4, 6, "_empty_.C2#")
    )
    val expectedDefinedSymbols2 = Set()
    assert(
      definedSymbols("C1")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.C1#`<init>`().") === expectedDefinedSymbols1)
    assert(
      definedSymbols("C2")
        .filterNot(_._3 == "_empty_.C2#`<init>`().") === expectedDefinedSymbols2)
  }

  "Type parameters" should "represented with TYPE_PARAMETER symbols" in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """class C[T1] {
                |  def m[T2[T3] <: Hi] = ???
                |  type T[T4 >: Lo] = ???
                |}
                |class Hi {}
                |class Lo {}""".stripMargin

    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, _, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedDefinedSymbols = Set(
      (0, 6, "_empty_.C#"),
      (0, 8, "_empty_.C#[T1]"),
      (1, 6, "_empty_.C#m()."),
      (1, 8, "_empty_.C#m().[T2]"),
      (1, 11, "_empty_.C#m().[T2][T3]"),
      (2, 7, "_empty_.C#T#"),
      (2, 9, "_empty_.C#T#[T4]"),
      (4, 6, "_empty_.Hi#"),
      (5, 6, "_empty_.Lo#")
    )
    assert(
      definedSymbols("C")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.C#`<init>`().") === expectedDefinedSymbols)
  }

  "Parameters" should "," in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """class C(p1: Int) {
                |  def m2(p2: Int) = ???
                |  def m3(p3: Int = 42) = ???
                |  def m4(p4: => Int) = ???
                |  def m5(p5: Int*) = ???
                |  def m6[C <% V] = ???
                |}
                |class V {}""".stripMargin

    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, referencedSymbols, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedReferencedSymbols = Set(
      (0, 12, "scala.Int#"),
      (1, 13, "scala.Int#"),
      (1, 20, "scala.Predef#$qmark$qmark$qmark()."),
      (2, 13, "scala.Int#"),
      (2, 25, "scala.Predef#$qmark$qmark$qmark()."),
      (3, 23, "scala.Predef#$qmark$qmark$qmark()."),
      (3, 16, "scala.Int#"),
      (4, 21, "scala.Predef#$qmark$qmark$qmark()."),
      (4, 13, "scala.Int#"),
      (5, 11, "scala.Function1#"),
      (5, 14, "_empty_.V#"),
      (5, 19, "scala.Predef#$qmark$qmark$qmark().")
    )
    assert(referencedSymbols("C").filterNot(isStandardNames) === expectedReferencedSymbols)

    val expectedDefinedSymbols = Set(
      (0, 6, "_empty_.C#"),
      (0, 8, "_empty_.C#p1."),
      (0, 8, "_empty_.C#`<init>`(Int).(p1)"),
      (1, 6, "_empty_.C#m2(Int)."),
      (1, 9, "_empty_.C#m2(Int).(p2)"),
      (2, 6, "_empty_.C#m3(Int)."),
      (2, 9, "_empty_.C#m3(Int).(p3)"),
      (2, 9, "_empty_.C#m3$default$1()."),
      (3, 6, "_empty_.C#m4(=>Int)."),
      (3, 9, "_empty_.C#m4(=>Int).(p4)"),
      (4, 6, "_empty_.C#m5(Int*)."),
      (4, 9, "_empty_.C#m5(Int*).(p5)"),
      (5, 6, "_empty_.C#m6(Function1)."),
      (5, 6, "_empty_.C#m6(Function1).(evidence$1)"),
      (5, 9, "_empty_.C#m6(Function1).[C]"),
      (7, 6, "_empty_.V#")
    )
    assert(
      definedSymbols("C")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.C#`<init>`(Int).") === expectedDefinedSymbols)
  }

  "Function declarations and definitions" should "are represented with METHOD symbols" in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """abstract class C {
                |  def m1: Int = ???
                |  def m2(): Int = ???
                |  def m3(x: Int): Int = ???
                |  def m3(x: org.Int): Int = ???
                |  def m4(x: Int)(y: Int): Int = ???
                |}
                |package org { class Int {}}""".stripMargin

    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, _, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedDefinedSymbols = Set(
      (0, 15, "_empty_.C#"),
      (1, 6, "_empty_.C#m1()."),
      (2, 6, "_empty_.C#m2()."),
      (3, 6, "_empty_.C#m3(Int)."),
      (3, 9, "_empty_.C#m3(Int).(x)"),
      //(4, 6, "_empty_.C#m3(Int+1)."),
      (4, 6, "_empty_.C#m3(Int)."), //TODO: Overloading with the same type name is not yet supported
      (4, 9, "_empty_.C#m3(Int).(x)"),
      (5, 6, "_empty_.C#m4(Int,Int)."),
      (5, 9, "_empty_.C#m4(Int,Int).(x)"),
      (5, 17, "_empty_.C#m4(Int,Int).(y)"),
      (7, 20, "org.Int#")
    )
    assert(
      definedSymbols("C")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.C#`<init>`().") === expectedDefinedSymbols)
  }

  "Constructors" should "are represented with CONSTRUCTOR symbols similarly to function definitions" in {
    //-----------0---------1---------2---------3---------4
    //-----------01234567890123456789012345678901234567890
    val src = """class C(x: Int) {
                |  def this() = this(42)
                |}""".stripMargin

    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (_, _, definedSymbols) =
      compilerForTesting.extractUsedNamesFromSrc(src)
    val expectedDefinedSymbols = Set(
      (0, 6, "_empty_.C#"),
      (0, 8, "_empty_.C#x."),
      (0, 8, "_empty_.C#`<init>`(Int).(x)"),
      (1, 6, "_empty_.C#`<init>`().")
    )
    assert(
      definedSymbols("C")
      // The primary constructor differs in Position depending on a scala version
        .filterNot(_._3 == "_empty_.C#`<init>`(Int).") === expectedDefinedSymbols)
  }

  private def isStandardNames(src: (Int, Int, String)): Boolean =
    src._3 == "scala.AnyRef#" || src._3 == "scala.Any#" || src._3 == "scala.Nothing#"

}
