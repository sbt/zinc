package sbt
package internal
package inc

import hedgehog.{ sbt => _, _ }
import hedgehog.runner._
import java.io.File

object ClassNameSpecification
    extends Properties
    with CompilingSpecification
    with BridgeProviderTestkit {
  override def tests: List[Test] = List(
    example(
      "ClassName should create correct binary names for top level object",
      expectBinaryClassNames("object A", Set("A" -> "A", "A" -> "A$"))
    ),
    example(
      "create binary names for top level class",
      expectBinaryClassNames("class A", Set("A" -> "A"))
    ),
    example(
      "create binary names for top level companions",
      expectBinaryClassNames("class A; object A", Set("A" -> "A", "A" -> "A$"))
    ),
    example("create binary names for case classes with no companions", lonelyCaseClass),
    example("create binary names for case classes with companions", caseClassCompanion),
    example("create a correct binary name for names with encoded symbols", encodedSymbols),
    example("create a correct binary name for names that are expanded", expandedNames),
    example("create correct binary names for nested object", nestedObjects),
    example("handle names of anonymous functions", anonymousFunctions),
    example("handle advanced scenarios of nested classes and objects", advancedNesting),
    example(
      "create a binary name for both class of the package objects and its classes",
      packageObject
    ),
    example(
      "create a binary name for a trait",
      // we do not track $impl classes because nobody can depend on them directly
      expectBinaryClassNames("trait A", Set("A" -> "A"))
    ),
    example("not create binary names nor class files for class of early inits", earlyInits),
    example(
      "not create binary names for a refinement class but register its class file",
      refinementClass
    ),
    example("not create binary names for local classes", localClasses),
    property("create binary names for any nesting of classes and objects", propNesting)
      .withTests(10),
  )

  private def lonelyCaseClass: Result =
    expectBinaryClassNames(
      "case class LonelyCaseClass(paramA: String)",
      Set("LonelyCaseClass" -> "LonelyCaseClass", "LonelyCaseClass" -> "LonelyCaseClass$")
    )

  private def caseClassCompanion: Result =
    expectBinaryClassNames(
      "case class LonelyCaseClass2(paramA: String);object LonelyCaseClass2 { val z: Int = 1 }",
      Set("LonelyCaseClass2" -> "LonelyCaseClass2", "LonelyCaseClass2" -> "LonelyCaseClass2$")
    )

  private def encodedSymbols: Result =
    expectBinaryClassNames(
      "package `package with spaces` { class :: }",
      Set(
        "package$u0020with$u0020spaces.$colon$colon" -> "package$u0020with$u0020spaces.$colon$colon"
      )
    )

  private def expandedNames: Result = {
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

  private def nestedObjects: Result =
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

  private def anonymousFunctions: Result =
    expectBinaryClassNames(
      "object A { val a: Unit = { println((a: String) => a) }}",
      Set("A" -> "A$", "A" -> "A"),
      if (scalaVersion.startsWith("2.10") || scalaVersion.startsWith("2.11")) Set("A$$anonfun$1")
      else Set.empty
    )

  private def advancedNesting: Result = {
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

  private def packageObject: Result =
    expectBinaryClassNames(
      "package object po { class B; object C }",
      Set(
        "po.package" -> "po.package",
        "po.package" -> "po.package$",
        "po.B" -> "po.package$B",
        "po.C" -> "po.package$C$"
      )
    )

  private def earlyInits: Result = {
    val src = """
      |class M extends {
      |  val a = 1
      |} with C
      |
      |abstract class C {
      |  val a: Int
      |}
      |""".stripMargin
    expectBinaryClassNames(src, Set("M" -> "M", "C" -> "C"))
  }

  private def refinementClass: Result = {
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
      Set("UseSite" -> "UseSite", "UseSite" -> "UseSite$", "C" -> "C", "C2" -> "C2"),
      // The anonymous
      Set("UseSite$$anon$1")
    )
  }

  private def localClasses: Result = {
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
      Set("Container" -> "Container", "T" -> "T"),
      // scala/scala#7203 (2.12.8, and 2.13 since M5) made LambdaLift's `renamable` set
      // insertion-ordered, which changed the order fresh indices are handed out to the
      // three `D` symbols, so `class D`/`object D` moved from $2/$3 to $1/$2.
      if (scalaVersion.startsWith("2.10") || scalaVersion.startsWith("2.11"))
        Set("Container$$anon$1", "Container$C$1", "Container$D$2", "Container$D$3$")
      else Set("Container$$anon$1", "Container$C$1", "Container$D$1", "Container$D$2$")
    )
  }

  /** A class or an object nested in a top level definition named after its position. */
  private final case class Node(isObject: Boolean, children: List[Node]) {
    def render(name: String): String = {
      val keyword = if (isObject) "object" else "class"
      val body = children.zipWithIndex.map { case (c, i) => c.render(s"${name}N$i") }
      s"$keyword $name { ${body.mkString("; ")} }"
    }

    /** The (source name, binary name) pairs, where only a top level object gets a mirror class. */
    def names(
        srcPrefix: String,
        binPrefix: String,
        name: String,
        topLevel: Boolean
    ): List[(String, String)] = {
      val src = srcPrefix + name
      val bin = binPrefix + name
      val own =
        if (!isObject) List(src -> bin)
        else if (topLevel) List(src -> s"$bin$$", src -> bin)
        else List(src -> s"$bin$$")
      own ++ children.zipWithIndex.flatMap {
        case (c, i) => c.names(s"$src.", s"$bin$$", s"${name}N$i", topLevel = false)
      }
    }
  }

  private def genNode(depth: Int): Gen[Node] =
    for {
      isObject <- Gen.boolean
      children <-
        if (depth == 0) Gen.constant(List.empty[Node])
        else Gen.list(genNode(depth - 1), Range.linear(0, 2))
    } yield Node(isObject, children)

  def propNesting: Property =
    for {
      roots <- Gen.list(genNode(2), Range.linear(1, 3)).forAll
    } yield {
      val named = roots.zipWithIndex.map { case (n, i) => (n, s"T$i") }
      val src = named.map { case (n, name) => n.render(name) }.mkString("\n")
      val expected = named.flatMap { case (n, name) => n.names("", "", name, topLevel = true) }
      expectBinaryClassNames(src, expected.toSet).log(src)
    }

  private def expectBinaryClassNames(
      src: String,
      expectedNames: Set[(String, String)],
      expectedLocalNames: Set[String] = Set.empty
  ): Result = {
    val (Seq(tempSrcFile), analysisCallback) = this.compileSrcs(src): @unchecked
    val binaryClassNames = analysisCallback.classNames(tempSrcFile).toSet
    val generatedProducts = analysisCallback.productClassesToSources.keySet.toSet
    if (binaryClassNames == expectedNames) {
      val paths =
        expectedLocalNames.map(n => s"${n}.class") ++
          expectedNames.map(n => s"${n._2.replace('.', File.separatorChar)}.class")
      val generatedProductNames = generatedProducts.map(_.getFileName.toString)
      val missing =
        generatedProducts.filter(gn => !paths.exists(p => gn.toAbsolutePath.endsWith(p)))
      val extra = paths.map(_.split(File.separatorChar).last).diff(generatedProductNames)
      if (missing.isEmpty && extra.isEmpty) Result.success
      else if (missing.nonEmpty && extra.nonEmpty)
        Result.failure.log(s"Missing classes $missing; extra classes $extra")
      else if (missing.nonEmpty) Result.failure.log(s"Missing classes $missing")
      else Result.failure.log(s"Extra classes $extra")
    } else {
      val isDisjoint = binaryClassNames.intersect(expectedNames).isEmpty
      val missing = binaryClassNames.diff(expectedNames).mkString
      val extra = expectedNames.diff(binaryClassNames).mkString
      Result.failure.log(
        if (isDisjoint) s"Received $binaryClassNames; expected $expectedNames"
        else if (missing.nonEmpty && extra.nonEmpty) s"Missing names $missing; extra names $extra"
        else if (missing.nonEmpty) s"Missing names $missing"
        else s"Extra names $extra"
      )
    }
  }
}
