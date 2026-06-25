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

import java.nio.file.Path

import xsbti.compile.IncToolOptionsUtil
import sbt.io.IO
import sbt.util.LoggerContext

/**
 * Tests the javac-AST based recovery of Java->Java dependencies on inlined `static final`
 * constants (sbt/zinc#145). javac inlines these constants and erases the reference to the declaring
 * class from the using class's bytecode, so they can only be recovered from the attributed AST.
 */
class JavaConstantDepsSpec extends UnitSpec {

  "Local javac constant analysis" should "record a member-select constant dependency" in deps {
    d => assert(d.getOrElse("p.A", Set.empty).contains("p.B"))
  }

  it should "record a statically-imported constant dependency (owner from the element)" in deps {
    d => assert(d.getOrElse("p.C", Set.empty).contains("p.B"))
  }

  it should "use the declaring class's binary name for a nested-class constant" in deps { d =>
    assert(d.getOrElse("p.E", Set.empty).contains("p.Outer$Inner"))
  }

  it should "not record dependencies on non-constant fields" in deps { d =>
    assert(!d.getOrElse("p.D", Set.empty).contains("p.B"))
  }

  // The cases that matter for sbt/zinc#145: javac erases the owner class entirely from the
  // bytecode (verified with javap), so these can ONLY be recovered from the AST.
  it should "record a constant used as an annotation value (owner erased from bytecode)" in deps {
    d => assert(d.getOrElse("p.AnnoUse", Set.empty).contains("p.B"))
  }

  it should "record a constant used as a switch-case label (owner erased from bytecode)" in deps {
    d => assert(d.getOrElse("p.SwitchUse", Set.empty).contains("p.B"))
  }

  // When an inherited constant is selected through a subtype (`Sub.K`), javac erases BOTH the
  // declaring class and the named subtype. Record both so a later change to either recompiles.
  it should "record the declaring owner of a constant inherited through a subtype" in deps { d =>
    assert(d.getOrElse("p.SubUse", Set.empty).contains("p.Base"))
  }

  it should "record the qualifier subtype of an inherited-constant reference" in deps { d =>
    assert(d.getOrElse("p.SubUse", Set.empty).contains("p.Sub"))
  }

  // Same as above but the subtype is named by a `import static` rather than at the use site.
  it should "record the declaring owner of a statically-imported inherited constant" in deps { d =>
    assert(d.getOrElse("p.StaticImportSub", Set.empty).contains("p.Base"))
  }

  it should "record the static-import subtype of an inherited constant" in deps { d =>
    assert(d.getOrElse("p.StaticImportSub", Set.empty).contains("p.Sub"))
  }

  // On-demand (wildcard) static import: the type is recovered via getAllMembers, not a name match.
  it should "record the owner of a wildcard-static-imported inherited constant" in deps { d =>
    assert(d.getOrElse("p.WildcardImportSub", Set.empty).contains("p.Base"))
  }

  it should "record the subtype of a wildcard-static-imported inherited constant" in deps { d =>
    assert(d.getOrElse("p.WildcardImportSub", Set.empty).contains("p.Sub"))
  }

  it should "not create a dependency from an unrelated explicit static import" in deps { d =>
    assert(d.getOrElse("p.NegImport", Set.empty).contains("p.Base")) // K's declaring owner
    assert(
      !d.getOrElse("p.NegImport", Set.empty).contains("p.Sub")
    ) // unrelated `import static Sub.M`
  }

  /** Compile the fixture set once and run the given assertion on the collected dependencies. */
  private def deps(check: Map[String, Set[String]] => Unit): Unit =
    IO.withTemporaryDirectory { tmp =>
      check(compileAndCollect(tmp.toPath))
    }

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

  private def compileAndCollect(tmp: Path): Map[String, Set[String]] = {
    val compiler = new LocalJavaCompiler(
      Option(javax.tools.ToolProvider.getSystemJavaCompiler)
        .getOrElse(sys.error("This test requires a JDK, not a JRE."))
    )
    val srcDir = tmp.resolve("src")
    val sources: Seq[Path] = fixtures.map {
      case (name, content) =>
        val f = srcDir.resolve(name)
        IO.write(f.toFile, content)
        f
    }
    val outDir = tmp.resolve("classes")
    IO.createDirectory(outDir.toFile)

    val log = LoggerContext.globalContext.logger("JavaConstantDepsSpec", None, None)
    val reporter = new ManagedLoggedReporter(10, log)
    val options =
      if (scala.util.Properties.isJavaAtLeast("21")) Array("-proc:none") else Array.empty[String]
    val (success, deps) = compiler.runWithConstantDeps(
      sources.map(PlainVirtualFile(_)).toArray,
      options,
      CompileOutput(outDir),
      IncToolOptionsUtil.defaultIncToolOptions(),
      reporter,
      log
    )
    assert(success, "javac compilation of the fixtures failed")
    deps
  }
}
