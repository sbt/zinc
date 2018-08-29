package xsbt

import java.io.File

import org.scalactic.source.Position
import sbt.internal.inc.UnitSpec

class ClassNameSpecification extends UnitSpec {
  "ClassName" should "create correct binary names for top level object" in {
    expectBinaryClassNames("object A", Set("A" -> "A", "A" -> "A$"))
  }

  it should "create binary names for top level class" in {
    expectBinaryClassNames("class A", Set("A" -> "A"))
  }

  it should "create binary names for top level companions" in {
    val src = "class A; object A"
    expectBinaryClassNames(src, Set("A" -> "A", "A" -> "A$"))
  }

  it should "create binary names for case classes with no companions" in {
    expectBinaryClassNames(
      "case class LonelyCaseClass(paramA: String)",
      Set("LonelyCaseClass" -> "LonelyCaseClass", "LonelyCaseClass" -> "LonelyCaseClass$")
    )
  }

  it should "create binary names for case classes with companions" in {
    expectBinaryClassNames(
      "case class LonelyCaseClass2(paramA: String);object LonelyCaseClass2 { val z: Int = 1 }",
      Set("LonelyCaseClass2" -> "LonelyCaseClass2", "LonelyCaseClass2" -> "LonelyCaseClass2$")
    )
  }

  it should "create a correct binary name for names with encoded symbols" in {
    val src = "package `package with spaces` { class :: }"
    expectBinaryClassNames(
      src,
      Set(
        "package$u0020with$u0020spaces.$colon$colon" -> "package$u0020with$u0020spaces.$colon$colon"
      )
    )
  }

  it should "create a correct binary name for names that are expanded" in {
    val src =
      """class Fooo {
        |  // This one is problematic because of expanded names
        |  private[Fooo] object Bar
        |}
        |
        |package issue127 {
        |  object Foo {
        |    private[issue127] class Bippy
        |    // This one is problematic because of expanded names
        |    private[issue127] object Bippy
        |  }
        |}
        |""".stripMargin
    expectBinaryClassNames(
      src,
      Set(
        "Fooo" -> "Fooo",
        "Fooo.Bar" -> "Fooo$Bar$",
        "issue127.Foo" -> "issue127.Foo",
        "issue127.Foo" -> "issue127.Foo$",
        "issue127.Foo.Bippy" -> "issue127.Foo$Bippy",
        "issue127.Foo.Bippy" -> "issue127.Foo$Bippy$"
      )
    )
  }

  it should "create correct binary names for nested object" in {
    expectBinaryClassNames(
      "object A { object C { object D } }; class B { object E }",
      Set(
        "A" -> "A$",
        "A" -> "A",
        "A.C" -> "A$C$",
        "A.C.D" -> "A$C$D$",
        "B" -> "B",
        "B.E" -> "B$E$"
      )
    )
  }

  it should "handle names of anonymous functions" in {
    val scalaVersion = scala.util.Properties.versionNumberString
    expectBinaryClassNames(
      "object A { val a: Unit = { println((a: String) => a) }}",
      Set(
        "A" -> "A$",
        "A" -> "A"
      ),
      if (scalaVersion.startsWith("2.10") || scalaVersion.startsWith("2.11")) Set("A$$anonfun$1")
      else Set.empty
    )
  }

  it should "handle advanced scenarios of nested classes and objects" in {
    val src =
      """
        |package foo.bar
        |
        |class A {
        |  class A2 {
        |    class A3 {
        |      object A4
        |    }
        |  }
        |}
        |object A {
        |  class B
        |  object B {
        |    class C
        |  }
        |}
      """.stripMargin

    expectBinaryClassNames(
      src,
      Set(
        "foo.bar.A" -> "foo.bar.A",
        "foo.bar.A" -> "foo.bar.A$",
        "foo.bar.A.A2" -> "foo.bar.A$A2",
        "foo.bar.A.A2.A3" -> "foo.bar.A$A2$A3",
        "foo.bar.A.A2.A3.A4" -> "foo.bar.A$A2$A3$A4$",
        "foo.bar.A.B" -> "foo.bar.A$B",
        "foo.bar.A.B" -> "foo.bar.A$B$",
        "foo.bar.A.B.C" -> "foo.bar.A$B$C"
      )
    )
  }

  it should "create a binary name for both class of the package objects and its classes" in {
    val src = "package object po { class B; object C }"
    expectBinaryClassNames(
      src,
      Set(
        "po.package" -> "po.package",
        "po.package" -> "po.package$",
        "po.B" -> "po.package$B",
        "po.C" -> "po.package$C$"
      )
    )
  }

  it should "create a binary name for a trait" in {
    // we do not track $impl classes because nobody can depend on them directly
    expectBinaryClassNames("trait A", Set("A" -> "A"))
  }

  it should "not create binary names nor class files for class of early inits" in {
    val src = """
      |class M extends {
      |  val a = 1
      |} with C
      |
      |abstract class C {
      |  val a: Int
      |}
      |""".stripMargin

    expectBinaryClassNames(
      src,
      Set(
        "M" -> "M",
        "C" -> "C"
      )
    )
  }

  it should "not create binary names for a refinement class but register its class file" in {
    val src = """
      |object UseSite {
      |  val rc: C with C2 { val a: Int } = new C with C2 {
      |    val a: Int = 1
      |  }
      |}
      |
      |abstract class C
      |trait C2
      |""".stripMargin

    expectBinaryClassNames(
      src,
      Set(
        "UseSite" -> "UseSite",
        "UseSite" -> "UseSite$",
        "C" -> "C",
        "C2" -> "C2"
      ),
      // The anonymous
      Set("UseSite$$anon$1")
    )
  }

  it should "not create binary names for local classes" in {
    val src = """
      |class Container {
      |  def foo = {
      |    class C
      |  }
      |  def baz = {
      |    class D(i: Int)
      |    object D
      |    new D(1)
      |  }
      |  def bar = {
      |    // anonymous class
      |    new T {}
      |  }
      |}
      |
      |trait T
      |""".stripMargin

    expectBinaryClassNames(
      src,
      Set(
        "Container" -> "Container",
        "T" -> "T"
      ),
      Set(
        "Container$$anon$1",
        "Container$C$1",
        "Container$D$2",
        "Container$D$3$"
      )
    )
  }

  private def expectBinaryClassNames(
      src: String,
      expectedNames: Set[(String, String)],
      expectedLocalNames: Set[String] = Set.empty
  )(implicit p: Position): Unit = {
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val (Seq(tempSrcFile), analysisCallback) = compilerForTesting.compileSrcs(src)
    val binaryClassNames = analysisCallback.classNames(tempSrcFile).toSet
    val generatedProducts = analysisCallback.productClassesToSources.keySet.toSet

    if (binaryClassNames === expectedNames) {
      val paths = (expectedLocalNames.map(n => s"${n}.class") ++
        expectedNames.map(n => s"${n._2.replace('.', File.separatorChar)}.class"))
      val generatedProductNames = generatedProducts.map(_.getName)
      val missing = {
        val ms = generatedProducts.map(gn => gn -> paths.find(p => gn.getAbsolutePath.endsWith(p)))
        ms.filter(_._2.isEmpty).map(_._1)
      }

      val extra = paths.map(_.split(File.separatorChar).last).diff(generatedProductNames)
      if (missing.isEmpty && extra.isEmpty) ()
      else {
        fail(
          if (missing.nonEmpty && extra.nonEmpty) s"Missing classes $missing; extra classes $extra"
          else if (missing.nonEmpty) s"Missing classes ${missing}"
          else s"Extra classes ${extra}"
        )
      }
    } else {
      val isDisjoint = binaryClassNames.intersect(expectedNames).isEmpty
      val missing = binaryClassNames.diff(expectedNames).mkString
      val extra = expectedNames.diff(binaryClassNames).mkString
      fail(
        if (isDisjoint) s"Received ${binaryClassNames}; expected ${expectedNames}"
        else if (missing.nonEmpty && extra.nonEmpty) s"Missing names $missing; extra names $extra"
        else if (missing.nonEmpty) s"Missing names ${missing}"
        else s"Extra names ${extra}"
      )
    }
  }
}
