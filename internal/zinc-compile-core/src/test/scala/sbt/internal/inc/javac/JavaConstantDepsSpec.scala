/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc
package javac

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.nio.file.Path
import xsbti.compile.IncToolOptionsUtil
import sbt.io.IO
import sbt.util.LoggerContext

/**
 * Tests the javac-AST based recovery of Java->Java dependencies on inlined `static final`
 * constants (sbt/zinc#145). javac inlines these constants and erases the reference to the declaring
 * class from the using class's bytecode, so they can only be recovered from the attributed AST.
 */
object JavaConstantDepsSpec extends Properties:
  override def tests: List[Test] = List(
    depends("record a member-select constant dependency", "p.A", "p.B"),
    depends(
      "record a statically-imported constant dependency (owner from the element)",
      "p.C",
      "p.B"
    ),
    depends(
      "use the declaring class's binary name for a nested-class constant",
      "p.E",
      "p.Outer$Inner"
    ),
    doesNotDepend("not record dependencies on non-constant fields", "p.D", "p.B"),
    // The cases that matter for sbt/zinc#145: javac erases the owner class entirely from the
    // bytecode (verified with javap), so these can ONLY be recovered from the AST.
    depends(
      "record a constant used as an annotation value (owner erased from bytecode)",
      "p.AnnoUse",
      "p.B"
    ),
    depends(
      "record a constant used as a switch-case label (owner erased from bytecode)",
      "p.SwitchUse",
      "p.B"
    ),
    // When an inherited constant is selected through a subtype (`Sub.K`), javac erases BOTH the
    // declaring class and the named subtype. Record both so a later change to either recompiles.
    depends(
      "record the declaring owner of a constant inherited through a subtype",
      "p.SubUse",
      "p.Base"
    ),
    depends("record the qualifier subtype of an inherited-constant reference", "p.SubUse", "p.Sub"),
    // Same as above but the subtype is named by a `import static` rather than at the use site.
    depends(
      "record the declaring owner of a statically-imported inherited constant",
      "p.StaticImportSub",
      "p.Base"
    ),
    depends(
      "record the static-import subtype of an inherited constant",
      "p.StaticImportSub",
      "p.Sub"
    ),
    // On-demand (wildcard) static import: the type is recovered via getAllMembers, not a name match.
    depends(
      "record the owner of a wildcard-static-imported inherited constant",
      "p.WildcardImportSub",
      "p.Base"
    ),
    depends(
      "record the subtype of a wildcard-static-imported inherited constant",
      "p.WildcardImportSub",
      "p.Sub"
    ),
    depends(
      "record the declaring owner of an inherited constant beside an unrelated static import",
      "p.NegImport",
      "p.Base" // K's declaring owner
    ),
    doesNotDepend(
      "not create a dependency from an unrelated explicit static import",
      "p.NegImport",
      "p.Sub" // unrelated `import static Sub.M`
    ),
    property("record the owner of any constant at any use site", propUseSites).withTests(10),
  )

  /** Compile the fixture set once and run the given assertion on the collected dependencies. */
  private lazy val fixtureDeps: Map[String, Set[String]] =
    IO.withTemporaryDirectory(tmp => compileAndCollect(tmp.toPath, fixtures))

  private def depends(name: String, from: String, on: String): Test =
    example(s"Local javac constant analysis should $name", hasDependency(fixtureDeps, from, on))

  private def doesNotDepend(name: String, from: String, on: String): Test =
    example(
      s"Local javac constant analysis should $name",
      Result.diff(fixtureDeps.getOrElse(from, Set.empty), on)(!_.contains(_))
    )

  private def hasDependency(deps: Map[String, Set[String]], from: String, on: String): Result =
    Result.diff(
      deps.getOrElse(from, Set.empty),
      on
    )(_.contains(_)).log(s"$from should depend on $on")

  private val fixtures: Seq[(String, String)] = Seq(
    "B.java" ->
      """package p;
        |public class B {
        |  public static final int MAX = 1;
        |  public int nonConst = 2;
        |}
        |""".stripMargin,
    "Outer.java" ->
      """package p;
        |public class Outer {
        |  public static class Inner {
        |    public static final int K = 7;
        |  }
        |}
        |""".stripMargin,
    "A.java" ->
      """package p;
        |public class A {
        |  int useConst() { return B.MAX; }
        |}
        |""".stripMargin,
    "C.java" ->
      """package p;
        |import static p.B.MAX;
        |public class C {
        |  int useImport() { return MAX; }
        |}
        |""".stripMargin,
    "D.java" ->
      """package p;
        |public class D {
        |  int useNonConst(B b) { return b.nonConst; }
        |}
        |""".stripMargin,
    "E.java" ->
      """package p;
        |public class E {
        |  int useNested() { return Outer.Inner.K; }
        |}
        |""".stripMargin,
    "Anno.java" ->
      """package p;
        |public @interface Anno { int value(); }
        |""".stripMargin,
    "AnnoUse.java" ->
      """package p;
        |@Anno(B.MAX)
        |public class AnnoUse {}
        |""".stripMargin,
    "SwitchUse.java" ->
      """package p;
        |public class SwitchUse {
        |  int g(int x) { switch (x) { case B.MAX: return 1; default: return 0; } }
        |}
        |""".stripMargin,
    "Base.java" ->
      """package p;
        |public class Base { public static final int K = 1; }
        |""".stripMargin,
    "Sub.java" ->
      """package p;
        |public class Sub extends Base { public static final int M = 2; }
        |""".stripMargin,
    "SubUse.java" ->
      """package p;
        |public class SubUse {
        |  int h(int x) { switch (x) { case Sub.K: return 1; default: return 0; } }
        |}
        |""".stripMargin,
    "StaticImportSub.java" ->
      """package p;
        |import static p.Sub.K;
        |public class StaticImportSub {
        |  int h(int x) { switch (x) { case K: return 1; default: return 0; } }
        |}
        |""".stripMargin,
    "WildcardImportSub.java" ->
      """package p;
        |import static p.Sub.*;
        |public class WildcardImportSub {
        |  int h(int x) { switch (x) { case K: return 1; default: return 0; } }
        |}
        |""".stripMargin,
    // Uses inherited K (extends Base) and has an UNRELATED explicit static import of Sub.M. The
    // import does not bring in K, so it must not create a dependency on Sub.
    "NegImport.java" ->
      """package p;
        |import static p.Sub.M;
        |public class NegImport extends Base {
        |  int h(int x) { switch (x) { case K: return 1; default: return 0; } }
        |}
        |""".stripMargin
  )

  private val useSites: List[(String, String) => String] = List(
    (owner, k) => s"public class Use { int f() { return $owner.$k; } }",
    (owner, k) => s"import static p.$owner.$k;\npublic class Use { int f() { return $k; } }",
    (owner, k) => s"import static p.$owner.*;\npublic class Use { int f() { return $k; } }",
    (owner, k) =>
      s"public class Use { int f(int x) { switch (x) { case $owner.$k: return 1; default: return 0; } } }",
    (owner, k) => s"@Anno($owner.$k)\npublic class Use {}",
  )

  def propUseSites: Property =
    for
      owner <- Gen.string(Gen.alphaNum, Range.linear(0, 8)).map("Owner" + _).forAll
      k <- Gen.string(Gen.alphaNum, Range.linear(0, 8)).map("K" + _).forAll
      value <- Gen.int(Range.linear(0, 1000)).forAll
      site <- Gen.elementUnsafe(useSites).forAll
    yield IO.withTemporaryDirectory { tmp =>
      val sources = Seq(
        s"$owner.java" ->
          s"package p;\npublic class $owner { public static final int $k = $value; }\n",
        "Anno.java" -> "package p;\npublic @interface Anno { int value(); }\n",
        "Use.java" -> s"package p;\n${site(owner, k)}\n",
      )
      hasDependency(compileAndCollect(tmp.toPath, sources), "p.Use", s"p.$owner")
    }

  private def compileAndCollect(
      tmp: Path,
      sourceFiles: Seq[(String, String)]
  ): Map[String, Set[String]] =
    val compiler = new LocalJavaCompiler(
      Option(javax.tools.ToolProvider.getSystemJavaCompiler)
        .getOrElse(sys.error("This test requires a JDK, not a JRE."))
    )
    val srcDir = tmp.resolve("src")
    val sources: Seq[Path] = sourceFiles.map { (name, content) =>
      val f = srcDir.resolve(name)
      IO.write(f.toFile, content)
      f
    }
    val outDir = tmp.resolve("classes")
    IO.createDirectory(outDir.toFile)
    val log = LoggerContext.globalContext.logger("JavaConstantDepsSpec", None, None)
    val reporter = new ManagedLoggedReporter(10, log)
    val options =
      if scala.util.Properties.isJavaAtLeast("21") then Array("-proc:none") else Array.empty[String]
    val (success, deps) = compiler.runWithConstantDeps(
      sources.map(PlainVirtualFile(_)).toArray,
      options,
      CompileOutput(outDir),
      IncToolOptionsUtil.defaultIncToolOptions(),
      reporter,
      log
    )
    if !success then
      sys.error(s"javac compilation of the fixtures failed: ${sourceFiles.map(_._2)}")
    deps
  end compileAndCollect
end JavaConstantDepsSpec
