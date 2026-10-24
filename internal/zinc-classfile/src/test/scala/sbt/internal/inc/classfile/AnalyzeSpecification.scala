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
package classfile

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.io.File
import sbt.io.IO
import xsbti.TestCallback
import xsbti.TestCallback.ExtractedClassDependencies
import xsbti.api.DependencyContext
import xsbti.api.DependencyContext.*

object AnalyzeSpecification extends Properties:
  override def tests: List[Test] = List(
    example("Analyze should extract dependencies of inner classes", innerClasses),
    example("process runtime-visible annotations", runtimeVisibleAnnotations),
    example("process annotation with array argument", arrayArgument),
    example("detect annotation in array argument to annotation", annotationInArrayArgument),
    example("process runtime-invisible annotations", runtimeInvisibleAnnotations),
    example("detect annotation on field", annotationOnField),
    example("detect annotation on method", annotationOnMethod),
    example("handle multiple annotations on field", multipleAnnotationsOnField),
    example(
      "record products for inner classes that cannot be reflectively loaded",
      unloadableInnerClass
    ),
    example("not record module-info as a generated class", moduleInfo),
    example(
      "record the transitive ancestry of a member-referenced type (sbt/zinc#148)",
      transitiveAncestry
    ),
    example(
      "record transitive ancestry of a referenced classpath type as binary deps (sbt/zinc#148)",
      classpathAncestry
    ),
    example(
      "track a javax.* ancestor served from the classpath, not the JDK (sbt/zinc#148)",
      javaxAncestor
    ),
    example("detect annotation on method parameter", annotationOnParameter),
    example("detect type-use annotation on a field type", typeUseOnField),
    example("detect type-use annotation on a local variable", typeUseOnLocal),
    example("detect annotation on a record component", annotationOnRecordComponent),
    example(
      "detect a type referenced by an annotation default value",
      annotationDefault
    ),
    example(
      "detect a type in an array class-literal annotation argument",
      arrayClassLiteral
    ),
    property("detect any annotation at any site that keeps it in the classfile", propAnnotations)
      .withTests(10),
  )

  private def deps(srcs: (String, String)*): ExtractedClassDependencies =
    JavaCompilerForUnitTesting.extractDependenciesFromSrcs(srcs*)

  private def refersTo(deps: ExtractedClassDependencies, from: String, on: String*): Result =
    Result.all(on.toList.map { name =>
      Result.diff(deps.memberRef(from), name)(_.contains(_)).log(s"$from should refer to $name")
    })

  private val runtimeRetention =
    """|import java.lang.annotation.Retention;
       |import java.lang.annotation.RetentionPolicy;
       |@Retention(RetentionPolicy.RUNTIME)
       |""".stripMargin

  private val printAnnotations =
    """|  public static void main(String[] args){
       |    System.out.println(Foo.class.getAnnotations().length);
       |  }
       |""".stripMargin

  private def innerClasses: Result =
    val srcA =
      """class A {
        |  class B {
        |    public D d = new D();
        |  }
        |}""".stripMargin
    val srcC =
      """
        |class C {
        |  A a = new A();
        |  A.B b = a.new B();
        |}""".stripMargin
    val srcD =
      """
        |class D {}
        |""".stripMargin
    val d = deps("A.java" -> srcA, "C.java" -> srcC, "D.java" -> srcD)
    Result.all(
      List(
        d.memberRef("A") ==== Set("A.B"),
        d.memberRef("A.B") ==== Set("A", "D"),
        d.memberRef("C") ==== Set("A", "A.B"),
        d.memberRef("D") ==== Set.empty,
      )
    )
  end innerClasses

  private def runtimeVisibleAnnotations: Result =
    val srcTest = runtimeRetention + "public @interface Test { }\n"
    val srcFoo = s"@Test\npublic class Foo {\n$printAnnotations}\n"
    refersTo(deps("Test.java" -> srcTest, "Foo.java" -> srcFoo), "Foo", "Test")

  private def arrayArgument: Result =
    val srcTest =
      s"""|${runtimeRetention}public @interface Test {
          |  java.lang.annotation.ElementType[] value();
          |}
          |""".stripMargin
    val srcFoo =
      s"""|import java.lang.annotation.ElementType;
          |@Test(ElementType.TYPE)
          |public class Foo {
          |$printAnnotations}
          |""".stripMargin
    refersTo(deps("Test.java" -> srcTest, "Foo.java" -> srcFoo), "Foo", "Test")

  private def annotationInArrayArgument: Result =
    val srcTest1 = runtimeRetention + "public @interface Test1 { }\n"
    val srcTest2 =
      s"""|${runtimeRetention}public @interface Test2 {
          |  Test1[] value();
          |}
          |""".stripMargin
    val srcFoo = s"@Test2(@Test1)\npublic class Foo {\n$printAnnotations}\n"
    refersTo(
      deps("Test1.java" -> srcTest1, "Test2.java" -> srcTest2, "Foo.java" -> srcFoo),
      "Foo",
      "Test1",
      "Test2"
    )

  private def runtimeInvisibleAnnotations: Result =
    val srcFoo = s"@Test\npublic class Foo {\n$printAnnotations}\n"
    refersTo(
      deps("Test.java" -> "public @interface Test { }\n", "Foo.java" -> srcFoo),
      "Foo",
      "Test"
    )

  private def targeted(target: String, name: String = "Test"): String =
    s"""|import java.lang.annotation.Target;
        |import java.lang.annotation.ElementType;
        |@Target(ElementType.$target)
        |public @interface $name { }
        |""".stripMargin

  private def annotationOnField: Result =
    val srcFoo = "public class Foo {\n  @Test int foo;\n}\n"
    refersTo(deps("Test.java" -> targeted("FIELD"), "Foo.java" -> srcFoo), "Foo", "Test")

  private def annotationOnMethod: Result =
    val srcFoo = "public class Foo {\n  @Test int foo() { return 0; }\n}\n"
    refersTo(deps("Test.java" -> targeted("METHOD"), "Foo.java" -> srcFoo), "Foo", "Test")

  // issue: sbt/sbt#6969
  private def multipleAnnotationsOnField: Result =
    val srcA1 = runtimeRetention + "public @interface A1 {\n  String s();\n}\n"
    val srcA2 = runtimeRetention + "public @interface A2 { }\n"
    val srcFoo = "public class Foo {\n  @A1(s = \"id\") @A2 String id;\n}\n"
    refersTo(
      deps("A1.java" -> srcA1, "A2.java" -> srcA2, "Foo.java" -> srcFoo),
      "Foo",
      "A1",
      "A2"
    )

  private def hasBinaryDependency(
      callback: TestCallback,
      on: String,
      from: String,
      context: DependencyContext
  ): Result =
    Result
      .assert(callback.binaryDependencies.exists {
        case (_, onName, fromName, ctx) => onName == on && fromName == from && ctx == context
      })
      .log(s"$from should have a $context binary dependency on $on")

  // sbt/zinc#837: an inner class whose superclass is inaccessible can't be reflectively loaded,
  // but JavaAnalyze should still record its product and member-ref deps from the classfile rather
  // than dropping it (or crashing).
  private def unloadableInnerClass: Result = IO.withTemporaryDirectory { temp =>
    val classesDir = new File(temp, "classes")
    val libDir = new File(temp, "lib")
    classesDir.mkdir()
    libDir.mkdir()
    // Compile against a public Base so Outer.Inner compiles cleanly.
    val baseFile = new File(temp, "Base.java")
    IO.write(baseFile, "package pkg; public class Base {}")
    JavaCompilerForUnitTesting.compileJava(Seq(baseFile), libDir, Seq.empty)
    val outerFile = new File(temp, "Outer.java")
    IO.write(
      outerFile,
      """|public class Outer {
         |  public class Inner extends pkg.Base {}
         |}
         |""".stripMargin
    )
    JavaCompilerForUnitTesting.compileJava(Seq(outerFile), classesDir, Seq(libDir))

    // Overwrite Base with a package-private version: Outer$Inner (in the default package) can no
    // longer access its superclass, so it fails to load with IllegalAccessError during analysis.
    IO.write(baseFile, "package pkg; class Base {}")
    JavaCompilerForUnitTesting.compileJava(Seq(baseFile), classesDir, Seq.empty)
    val callback = JavaCompilerForUnitTesting.analyze(classesDir, Seq(outerFile))
    val products = callback.productClassesToSources.keySet.map(_.getFileName.toString)
    val recordedNames = callback.classNames.values.flatten.toSet
    Result.all(
      List(
        // The product for the un-loadable inner class is still recorded, derived from the classfile.
        Result.assert(products.contains("Outer.class")).log(s"products: $products"),
        Result.assert(products.contains("Outer$Inner.class")).log(s"products: $products"),
        // ... under its classfile-derived source (canonical) name.
        Result.assert(recordedNames.contains(("Outer.Inner", "Outer$Inner"))),
        // Member-ref deps to the un-loadable class are recorded (Outer references Outer.Inner) ...
        Result.assert(
          callback.classDependencies.contains(("Outer.Inner", "Outer", DependencyByMemberRef))
        ),
        // ... as are member-ref deps from it (Outer.Inner references its external superclass pkg.Base).
        hasBinaryDependency(callback, "pkg.Base", "Outer.Inner", DependencyByMemberRef),
        // ... and crucially the inheritance edge that caused the issue (Outer.Inner extends pkg.Base),
        // recovered from the classfile since the class can't be loaded to extract its API.
        hasBinaryDependency(callback, "pkg.Base", "Outer.Inner", DependencyByInheritance),
      )
    )
  }

  // sbt/zinc#837 regression: the unloadable-class fallback must not record module-info.class, which
  // load() deliberately skips (it is not a real class).
  private def moduleInfo: Result = IO.withTemporaryDirectory { temp =>
    val classesDir = new File(temp, "classes")
    classesDir.mkdir()
    val moduleInfo = new File(temp, "module-info.java")
    IO.write(moduleInfo, "module foo {}")
    val fooFile = new File(temp, "Foo.java")
    IO.write(fooFile, "package p; public class Foo {}")
    JavaCompilerForUnitTesting.compileJava(Seq(moduleInfo, fooFile), classesDir, Seq.empty)
    val callback = JavaCompilerForUnitTesting.analyze(classesDir, Seq(moduleInfo, fooFile))
    val products = callback.productClassesToSources.keySet.map(_.getFileName.toString)
    val recordedNames = callback.classNames.values.flatten.toSet.map(_._1)
    Result.all(
      List(
        Result.assert(products.contains("Foo.class")).log(s"products: $products"),
        Result.assert(!products.contains("module-info.class")).log(s"products: $products"),
        Result.assert(!recordedNames.contains("module-info")).log(s"names: $recordedNames"),
      )
    )
  }

  // sbt/zinc#148: Test only names Foo, but javac needs Foo's whole ancestry present, so it is
  // recorded as member-ref deps of Test.
  private def transitiveAncestry: Result =
    val srcFoo =
      """|public class Foo extends Base {
         |  public void base() {}
         |  public void foo() {}
         |  public void other() {}
         |}
         |""".stripMargin
    val d = deps(
      "IBase.java" -> "public interface IBase { void base(); }\n",
      "IFoo.java" -> "public interface IFoo extends IBase { void foo(); }\n",
      "Base.java" -> "public abstract class Base implements IFoo {}\n",
      "Foo.java" -> srcFoo,
      "Test.java" -> "public class Test {\n  void m(Foo f) { f.other(); }\n}\n",
    )
    Result.all(
      List(
        // Recorded as member-ref, not inheritance: Test uses Foo without inheriting from it.
        d.memberRef("Test") ==== Set("Foo", "Base", "IFoo", "IBase"),
        d.inheritance("Test") ==== Set.empty,
        // The hierarchy is also still tracked as direct-parent inheritance edges on each owner.
        d.inheritance("Foo") ==== Set("Base"),
        d.inheritance("Base") ==== Set("IFoo"),
        d.inheritance("IFoo") ==== Set("IBase"),
      )
    )
  end transitiveAncestry

  // sbt/zinc#148: the ancestry of a referenced classpath (library) type is recorded too, as
  // binary deps.
  private def classpathAncestry: Result = IO.withTemporaryDirectory { temp =>
    val classesDir = new File(temp, "classes")
    classesDir.mkdir()
    val libIface = new File(temp, "LibIface.java")
    IO.write(libIface, "package pkg; public interface LibIface {}")
    val libBase = new File(temp, "LibBase.java")
    IO.write(libBase, "package pkg; public abstract class LibBase {}")
    val lib = new File(temp, "Lib.java")
    IO.write(
      lib,
      "package pkg; public class Lib extends LibBase implements LibIface { public void hello() {} }"
    )
    val client = new File(temp, "Client.java")
    IO.write(client, "public class Client { void m(pkg.Lib x) { x.hello(); } }")
    // Compile the library and the client together, then analyze ONLY Client — so the library types
    // are resolved as classpath classfiles (via the classloader), exactly like a real dependency jar.
    JavaCompilerForUnitTesting.compileJava(
      Seq(libIface, libBase, lib, client),
      classesDir,
      Seq.empty
    )
    val callback = JavaCompilerForUnitTesting.analyze(classesDir, Seq(client))
    Result.all(
      List(
        hasBinaryDependency(
          callback,
          "pkg.Lib",
          "Client",
          DependencyByMemberRef
        ), // the directly referenced type
        hasBinaryDependency(
          callback,
          "pkg.LibBase",
          "Client",
          DependencyByMemberRef
        ), // transitive superclass
        hasBinaryDependency(
          callback,
          "pkg.LibIface",
          "Client",
          DependencyByMemberRef
        ), // transitive interface
      )
    )
  }

  // sbt/zinc#148 regression: javax.* ancestors are ordinary classpath jars, so origin-based (not
  // package-prefix) platform detection must keep tracking them.
  private def javaxAncestor: Result = IO.withTemporaryDirectory { temp =>
    val classesDir = new File(temp, "classes")
    classesDir.mkdir()
    // A user class in a javax.* package (only java.* is reserved) — like a servlet base in a
    // dependency jar.
    val base = new File(temp, "Base.java")
    IO.write(base, "package javax.foo; public class Base {}")
    val foo = new File(temp, "Foo.java")
    IO.write(foo, "public class Foo extends javax.foo.Base { public void hi() {} }")
    val client = new File(temp, "Client.java")
    IO.write(client, "public class Client { void m(Foo f) { f.hi(); } }")
    JavaCompilerForUnitTesting.compileJava(Seq(base, foo, client), classesDir, Seq.empty)
    val callback = JavaCompilerForUnitTesting.analyze(classesDir, Seq(client))
    // javax.foo.Base resolves to a classpath classfile (not `jrt:`), so the ancestry walk records it.
    hasBinaryDependency(callback, "javax.foo.Base", "Client", DependencyByMemberRef)
  }

  // issue: sbt/zinc#146 (parameter annotations, JVMS 4.7.18)
  private def annotationOnParameter: Result =
    val srcTest = runtimeRetention + "public @interface Test { }\n"
    val srcFoo = "public class Foo {\n  int foo(@Test int x) { return x; }\n}\n"
    refersTo(deps("Test.java" -> srcTest, "Foo.java" -> srcFoo), "Foo", "Test")

  // issue: sbt/zinc#146 (type annotations, JVMS 4.7.20)
  private def typeUseOnField: Result =
    val srcFoo = "public class Foo {\n  @Test String s = \"\";\n}\n"
    refersTo(deps("Test.java" -> targeted("TYPE_USE"), "Foo.java" -> srcFoo), "Foo", "Test")

  // issue: sbt/zinc#146 (type annotations nested in the Code attribute, JVMS 4.7.3)
  private def typeUseOnLocal: Result =
    val srcFoo = "public class Foo {\n  void m() {\n    @Test String s = \"\";\n  }\n}\n"
    refersTo(deps("Test.java" -> targeted("TYPE_USE"), "Foo.java" -> srcFoo), "Foo", "Test")

  // issue: sbt/zinc#146 (annotations on record components, JVMS 4.7.30)
  private def annotationOnRecordComponent: Result =
    val srcFoo = "public record Foo(@Test int x) { }\n"
    refersTo(
      deps("Test.java" -> targeted("RECORD_COMPONENT"), "Foo.java" -> srcFoo),
      "Foo",
      "Test"
    )

  // issue: sbt/zinc#146 (types referenced by an annotation default, JVMS 4.7.22)
  private def annotationDefault: Result =
    val srcHolder = "public @interface Holder {\n  Color color() default Color.RED;\n}\n"
    refersTo(
      deps("Color.java" -> "public enum Color { RED, GREEN }\n", "Holder.java" -> srcHolder),
      "Holder",
      "Color"
    )

  // issue: sbt/zinc#146 (class-literal element value stored as an array descriptor `[LBar;`)
  private def arrayClassLiteral: Result =
    val srcTest = runtimeRetention + "public @interface Test { Class<?> value(); }\n"
    val d = deps(
      "Test.java" -> srcTest,
      "Bar.java" -> "public class Bar { }\n",
      "Foo.java" -> "@Test(Bar[].class)\npublic class Foo { }\n",
    )
    refersTo(d, "Foo", "Bar")

  /** Declaration sites, with the `@Target` each needs; local variables keep only type-use annotations. */
  private val sites: List[(String, String => String)] = List(
    "TYPE" -> (a => s"@$a public class Foo { }"),
    "FIELD" -> (a => s"public class Foo { @$a int foo; }"),
    "METHOD" -> (a => s"public class Foo { @$a int foo() { return 0; } }"),
    "PARAMETER" -> (a => s"public class Foo { int foo(@$a int x) { return x; } }"),
    "CONSTRUCTOR" -> (a => s"public class Foo { @$a public Foo() { } }"),
    "TYPE_USE" -> (a => s"public class Foo { @$a String s = \"\"; }"),
    "TYPE_USE" -> (a => s"public class Foo { void m() { @$a String s = \"\"; } }"),
  )

  def propAnnotations: Property =
    for
      name <- Gen.string(Gen.alphaNum, Range.linear(0, 8)).map("Ann" + _).forAll
      retention <- Gen.element1("RUNTIME", "CLASS").forAll
      (target, site) <- Gen.elementUnsafe(sites).forAll
    yield
      val srcAnnotation =
        s"""|import java.lang.annotation.*;
            |@Retention(RetentionPolicy.$retention)
            |@Target(ElementType.$target)
            |public @interface $name { }
            |""".stripMargin
      refersTo(deps(s"$name.java" -> srcAnnotation, "Foo.java" -> site(name)), "Foo", name)
        .log(site(name))
end AnalyzeSpecification
