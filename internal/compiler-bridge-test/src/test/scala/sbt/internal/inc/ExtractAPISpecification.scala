package sbt
package internal
package inc

import hedgehog.{ sbt => _, _ }
import hedgehog.runner._
import xsbti.api._
import xsbt.api.{ SameAPI, ShowAPI }

object ExtractAPISpecification
    extends Properties
    with CompilingSpecification
    with BridgeProviderTestkit {
  override def tests: List[Test] = List(
    example(
      "ExtractAPI should give stable names to members of existential types in method signatures",
      stableExistentialNames
    ),
    example("extract children of a sealed class", sealedChildren),
    example("extract correctly the definition type of a package object", packageObject),
    example("extract nested classes", nestedClasses),
    example("not extract local classes", localClasses),
    example("extract flat (without members) api for a nested class", flatNestedApi),
    property("extract a flat api for a nested class with any members", propFlatNestedApi)
      .withTests(5),
    example("extract private classes", privateClasses),
    property("extract every class of any nesting", propNesting).withTests(10),
    example(
      "make a stable representation of a self variable that has no self type",
      selfVariable
    ),
    example("make a different representation for an inherited class", inheritedClass),
    example("handle package objects and type companions", typeCompanions),
    example("give a stable modifier to a refinement member (#88)", refinementMember),
    example(
      "give stable names to type parameters owned by a refinement class (sbt/sbt#1079)",
      refinementTypeParameters
    ),
    example("represent a self type correctly", selfTypes),
    example("print the arguments of static annotations", annotationArguments),
  )

  private def fooClassApi(src: String): ClassLike =
    extractApisFromSrc(src).find(_.name() == "Foo").get

  private def stableExistentialNames: Result = {
    def compileAndGetFooMethodApi(src: String): Def =
      fooClassApi(src).structure().declared().find(_.name == "foo").get.asInstanceOf[Def]
    val src1 = """
				|class Box[T]
				|class Foo {
				|	def foo: Box[_] = null
				|
				}""".stripMargin
    val src2 = """
				|class Box[T]
				|class Foo {
			    |   def bar: Box[_] = null
				|	def foo: Box[_] = null
				|
				}""".stripMargin
    Result
      .assert(SameAPI(compileAndGetFooMethodApi(src1), compileAndGetFooMethodApi(src2)))
      .log("APIs are not the same.")
  }

  private def sealedChildren: Result = {
    val src1 =
      """|sealed abstract class Foo
         |case class C1(x: Int) extends Foo
         |""".stripMargin
    val src2 =
      """|sealed abstract class Foo
         |case class C1(x: Int) extends Foo
         |case class C2(x: Int) extends Foo
         |""".stripMargin
    Result.assert(!SameAPI(fooClassApi(src1), fooClassApi(src2)))
  }

  private def packageObject: Result =
    extractApisFromSrc("package object foo").toList.map(_.definitionType) ====
      List(DefinitionType.PackageModule)

  private def apiNames(src: String): Set[String] = extractApisFromSrc(src).map(_.name)

  private def nestedClasses: Result =
    apiNames("class A {\n  class B\n}") ==== Set("A", "A.B")

  private def localClasses: Result = {
    val src =
      """class A
        |class B
        |class C { def foo: Unit = { class Inner2 extends B } }
        |class D { def foo: Unit = { new B {} } }""".stripMargin
    apiNames(src) ==== Set("A", "B", "C", "D")
  }

  private def flatNestedApi: Result = {
    val src1 =
      """class Foo {
        |  class A
        |}""".stripMargin
    val src2 =
      """class Foo {
        |  class A {
        |    def foo: Int = 123
        |  }
        |}""".stripMargin
    Result.assert(SameAPI(fooClassApi(src1), fooClassApi(src2)))
  }

  private val genMember: Gen[String] =
    for {
      name <- Gen.string(Gen.lower, Range.linear(1, 6)).map("m" + _)
      member <- Gen.element1(
        s"def $name: Int = 1",
        s"val $name = \"\"",
        s"type ${name.capitalize} = Int"
      )
    } yield member

  def propFlatNestedApi: Property =
    for {
      members <- Gen.list(genMember, Range.linear(1, 4)).forAll
    } yield {
      val withMembers =
        members.distinct.mkString("class Foo {\n  class A {\n    ", "\n    ", "\n  }\n}")
      Result
        .assert(SameAPI(fooClassApi("class Foo {\n  class A\n}"), fooClassApi(withMembers)))
        .log(withMembers)
    }

  private def privateClasses: Result = {
    val src =
      """private class A
        |class B { private class Inner1 extends A }
        |""".stripMargin
    apiNames(src) ==== Set("A", "B", "B.Inner1")
  }

  /** A class nested in a top level class, named after its position. */
  private final case class Nested(modifier: String, children: List[Nested]) {
    def render(name: String): String = {
      val body = children.zipWithIndex.map { case (c, i) => c.render(s"${name}N$i") }
      s"${modifier}class $name { ${body.mkString("; ")} }"
    }

    def names(prefix: String, name: String): List[String] = (prefix + name) ::
      children.zipWithIndex.flatMap {
        case (c, i) => c.names(s"$prefix$name.", s"${name}N$i")
      }
  }

  private def genNested(depth: Int): Gen[Nested] =
    for {
      modifier <- Gen.element1("", "private ", "protected ", "final ")
      children <-
        if (depth == 0) Gen.constant(List.empty[Nested])
        else Gen.list(genNested(depth - 1), Range.linear(0, 2))
    } yield Nested(modifier, children)

  def propNesting: Property =
    for {
      roots <- Gen.list(genNested(2).map(_.copy(modifier = "")), Range.linear(1, 3)).forAll
    } yield {
      val named = roots.zipWithIndex.map { case (n, i) => (n, s"T$i") }
      val src = named.map { case (n, name) => n.render(name) }.mkString("\n")
      (apiNames(src) ==== named.flatMap { case (n, name) => n.names("", name) }.toSet).log(src)
    }

  /**
   * Checks if representation of the inherited Namer class (with a declared self variable) in Global.Foo
   * is stable between compiling from source and unpickling. We compare extracted APIs of Global when Global
   * is compiled together with Namers or Namers is compiled first and then Global refers
   * to Namers by unpickling types from class files.
   */
  private def selfVariable: Result = {
    val src1 =
      """|class Namers {
        |  class Namer { thisNamer => }
        |}
        |""".stripMargin
    val src2 =
      """|class Global {
        |  class Foo extends Namers
        |}
        |""".stripMargin
    val apis = extractApisFromSrcs(List(src1, src2), List(src2))
    val _ :: src2Api1 :: src2Api2 :: Nil = apis.toList: @unchecked
    def selectNamer(apis: Set[ClassLike]): ClassLike = {
      // TODO: this doesn't work yet because inherited classes are not extracted
      apis.find(_.name == "Global.Foo.Namer").get
    }
    Result.assert(SameAPI(selectNamer(src2Api1), selectNamer(src2Api2)))
  }

  private def inheritedClass: Result = {
    val src =
      """|class A[T] {
         |  abstract class AA { def t: T }
         |}
         |class B extends A[Int]
      """.stripMargin
    val apis = extractApisFromSrc(src).map(a => a.name -> a).toMap
    Result.all(
      List(
        apis.keySet ==== Set("A", "A.AA", "B", "B.AA"),
        Result.diff(apis.get("A.AA"), apis.get("B.AA"))(_ != _),
      )
    )
  }

  private def typeCompanions: Result = {
    val src =
      """|package object abc {
         |  type BuildInfoKey = BuildInfoKey.Entry[_]
         |  object BuildInfoKey {
         |    sealed trait Entry[A]
         |  }
         |}
      """.stripMargin
    apiNames(src) ==== Set("abc.package", "abc.BuildInfoKey", "abc.BuildInfoKey.Entry")
  }

  /**
   * Checks if self type is properly extracted in various cases of declaring a self type
   * with our without a self variable.
   */
  // Regression guard for https://github.com/sbt/zinc/issues/88.
  // A member of a refinement type (here `universe` in `Context { val universe: ... }`,
  // the inferred type of `_openMacros`) must get a stable modifier set whether the
  // enclosing types are compiled from source or unpickled from class files. Otherwise a
  // spurious `override` flickers onto the member (scala/bug#7361), changing the API hash
  // across compiles and forcing needless recompilation of dependents. Both the reporter's
  // original (`List`) and minimized (vararg) forms are covered.
  private def refinementMember: Result = {
    val deps =
      """|class Global
         |trait Analyzer extends AnyRef with StdAttachments { val global: Global }
         |trait StdAttachments { self: Analyzer =>
         |  type MacroContext = Context { val universe: self.global.type }
         |}
         |abstract class Context { val universe: Global }
         |class Foo[A]
         |""".stripMargin
    val macros =
      """|trait Macros { self: Analyzer =>
         |  // original report: inferred List type over the refinement
         |  var _openMacrosOrig = List[MacroContext]()
         |  def pushMacroContext(c: MacroContext) = _openMacrosOrig ::= c
         |  // minimized report: inferred type via a vararg method
         |  val _openMacros = foo[MacroContext]()
         |  def foo[A](xs: A*): Foo[A] = null
         |}
         |""".stripMargin
    // compile everything together (from source), then recompile `macros` alone,
    // unpickling the support types from class files.
    val apis = extractApisFromSrcs(List(deps, macros), List(macros))
    val _ :: macrosFromSource :: macrosUnpickled :: Nil = apis.toList: @unchecked
    def macrosClass(as: Set[ClassLike]): ClassLike = as.find(_.name == "Macros").get
    Result
      .assert(SameAPI(macrosClass(macrosFromSource), macrosClass(macrosUnpickled)))
      .log("Macros API differs between compiling from source and unpickling")
  }

  // Regression guard for https://github.com/sbt/sbt/issues/1079.
  // A type parameter of a type alias declared inside a refinement (a type lambda) is named
  // by `Symbol.fullName`, whose owner chain passes through the anonymous `<refinement>`
  // class. The pickler rewrites that class's owner (scala/bug#6596), so the parameter is
  // named `test.<refinement>.a` from source and `test.KleisliMonadReader.<refinement>.a`
  // when unpickled, flipping the API hash of every class built on the type lambda.
  private def refinementTypeParameters: Result = {
    val monadReader =
      """|package test
         |trait MonadReader[F[_], R] {
         |  def ask: F[R]
         |  def local[A](f: R => R)(fa: F[A]): F[A]
         |}
         |""".stripMargin
    val kleisli =
      """|package test
         |case class Kleisli[F[_], R, A](run: R => F[A])
         |trait KleisliMonadReader[F[_], R]
         |    extends MonadReader[({ type l[a] = Kleisli[F, R, a] })#l, R] {
         |  def ask: Kleisli[F, R, R] = ???
         |  def local[A](f: R => R)(fa: Kleisli[F, R, A]): Kleisli[F, R, A] = ???
         |}
         |""".stripMargin
    val impl =
      """|package test
         |class Impl extends KleisliMonadReader[Option, Int]
         |""".stripMargin
    // compile everything together (from source), then recompile `impl` alone,
    // unpickling the support types from class files.
    val apis = extractApisFromSrcs(List(monadReader, kleisli, impl), List(impl))
    val _ :: _ :: implFromSource :: implUnpickled :: Nil = apis.toList: @unchecked
    def implClass(as: Set[ClassLike]): ClassLike = as.find(_.name == "test.Impl").get
    // nesting 10: the type lambda sits in a parent's type argument, deeper than
    // DefaultShowAPI's default of 2 reaches.
    def render(c: ClassLike): String = ShowAPI.showApi(c)(using 10)
    val fromSource = implClass(implFromSource)
    val unpickled = implClass(implUnpickled)
    val fromSourceText = render(fromSource)
    val unpickledText = render(unpickled)
    val renderings = List("from source" -> fromSourceText, "unpickled" -> unpickledText)
    Result.all(
      Result
        .assert(SameAPI(fromSource, unpickled))
        .log(
          s"Impl API differs between compiling from source and unpickling:" +
            s"\nfrom source:$fromSourceText\nunpickled:$unpickledText"
        ) :: renderings.flatMap {
        case (label, rendered) =>
          List(
            Result.assert(rendered.contains("<refinement>.a")).log(s"$label:\n$rendered"),
            // no owner prefix survives; this test has a single, non-nested refinement
            Result.assert(!rendered.contains(".<refinement>")).log(s"$label:\n$rendered"),
          )
      }
    )
  }

  private def selfTypes: Result = {
    val srcs = List(
      "trait X",
      "trait Y",
      "class C1 { this: C1 => }",
      "class C2 { thisC: C2 => }",
      "class C3 { this: X => }",
      "class C4 { thisC: X => }",
      "class C5 extends AnyRef with X with Y { self: X with Y => }",
      "class C6 extends AnyRef with X { self: X with Y => }",
      "class C7 { _ => }",
      "class C8 { self => }",
    )
    val apis = extractApisFromSrcs(srcs).map(_.head)
    val emptyType = EmptyType.of()
    val (withSelfType, withoutSelfType) = apis.partition(_.selfType != emptyType)
    Result.all(
      List(
        withSelfType.map(_.name).toSet ==== Set("C3", "C4", "C5", "C6"),
        withoutSelfType.map(_.name).toSet ==== Set("X", "Y", "C1", "C2", "C7", "C8"),
      )
    )
  }

  // Annotation arguments are printed through one reused printer, whose buffer must be reset
  // between annotations.
  private def annotationArguments: Result = {
    val src =
      """|class ann(x: Int, s: String) extends scala.annotation.StaticAnnotation
         |class noArgs extends scala.annotation.StaticAnnotation
         |class C {
         |  @ann(1, "s") @noArgs @ann(-2, "t") def f = 1
         |}
         |""".stripMargin
    val c = extractApisFromSrc(src).find(_.name == "C").get
    val f = c.structure.declared.find(_.name == "f").get
    val printed =
      f.annotations.toList.map(a => a.arguments.toList.map(arg => (arg.name, arg.value)))
    printed ==== List(List(("", "(1,\"s\")")), List(("", "()")), List(("", "(-2,\"t\")")))
  }
}
