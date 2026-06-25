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

import java.io.File
import sbt.io.IO
import xsbti.api.DependencyContext._

class AnalyzeSpecification extends UnitSpec {

  "Analyze" should "extract dependencies of inner classes" in {
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

    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "A.java" -> srcA,
      "C.java" -> srcC,
      "D.java" -> srcD
    )

    assert(deps.memberRef("A") === Set("A.B"))
    assert(deps.memberRef("A.B") === Set("A", "D"))
    assert(deps.memberRef("C") === Set("A", "A.B"))
    assert(deps.memberRef("D") === Set.empty)
  }

  "Analyze" should "process runtime-visible annotations" in {
    val srcTest =
      """|import java.lang.annotation.Retention;
         |import java.lang.annotation.RetentionPolicy;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface Test { }
         |""".stripMargin
    val srcFoo =
      """|@Test
         |public class Foo {
         |  public static void main(String[] args){
         |    System.out.println(Foo.class.getAnnotations().length);
         |  }
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  "Analyze" should "process annotation with array argument" in {
    val srcTest =
      """|import java.lang.annotation.Retention;
         |import java.lang.annotation.RetentionPolicy;
         |import java.lang.annotation.ElementType;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface Test {
         |  ElementType[] value();
         |}
         |""".stripMargin
    val srcFoo =
      """|import java.lang.annotation.ElementType;
         |@Test(ElementType.TYPE)
         |public class Foo {
         |  public static void main(String[] args){
         |    System.out.println(Foo.class.getAnnotations().length);
         |  }
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  "Analyze" should "detect annotation in array argument to annotation" in {
    val srcTest1 =
      """|import java.lang.annotation.Retention;
         |import java.lang.annotation.RetentionPolicy;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface Test1 { }
         |""".stripMargin
    val srcTest2 =
      """|import java.lang.annotation.Retention;
         |import java.lang.annotation.RetentionPolicy;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface Test2 {
         |  Test1[] value();
         |}
         |""".stripMargin
    val srcFoo =
      """|import java.lang.annotation.ElementType;
         |@Test2(@Test1)
         |public class Foo {
         |  public static void main(String[] args){
         |    System.out.println(Foo.class.getAnnotations().length);
         |  }
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test1.java" -> srcTest1,
      "Test2.java" -> srcTest2,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test1"))
    assert(deps.memberRef("Foo").contains("Test2"))
  }

  "Analyze" should "process runtime-invisible annotations" in {
    val srcTest =
      """|public @interface Test { }
         |""".stripMargin
    val srcFoo =
      """|@Test
         |public class Foo {
         |  public static void main(String[] args){
         |    System.out.println(Foo.class.getAnnotations().length);
         |  }
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  "Analyze" should "detect annotation on field" in {
    val srcTest =
      """|import java.lang.annotation.Target;
         |import java.lang.annotation.ElementType;
         |@Target(ElementType.FIELD)
         |public @interface Test { }
         |""".stripMargin
    val srcFoo =
      """|public class Foo {
         |  @Test int foo;
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  "Analyze" should "detect annotation on method" in {
    val srcTest =
      """|import java.lang.annotation.Target;
         |import java.lang.annotation.ElementType;
         |@Target(ElementType.METHOD)
         |public @interface Test { }
         |""".stripMargin
    val srcFoo =
      """|public class Foo {
         |  @Test int foo() { return 0; }
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  // issue: sbt/sbt#6969
  "Analyze" should "handle multiple annotations on field" in {
    val srcA1 =
      """|import java.lang.annotation.Retention;
         |import java.lang.annotation.RetentionPolicy;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface A1 {
         |  String s();
         |}
         |""".stripMargin
    val srcA2 =
      """|import java.lang.annotation.Retention;
         |import java.lang.annotation.RetentionPolicy;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface A2 { }
         |""".stripMargin
    val srcFoo =
      """|public class Foo {
         |  @A1(s = "id") @A2 String id;
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "A1.java" -> srcA1,
      "A2.java" -> srcA2,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("A1"))
    assert(deps.memberRef("Foo").contains("A2"))
  }

  // sbt/zinc#837: an inner class whose superclass is inaccessible can't be reflectively loaded,
  // but JavaAnalyze should still record its product and member-ref deps from the classfile rather
  // than dropping it (or crashing).
  "Analyze" should "record products for inner classes that cannot be reflectively loaded" in {
    IO.withTemporaryDirectory { temp =>
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

      // The product for the un-loadable inner class is still recorded, derived from the classfile.
      val products = callback.productClassesToSources.keySet.map(_.getFileName.toString)
      assert(products.contains("Outer.class"))
      assert(products.contains("Outer$Inner.class"))

      // ... under its classfile-derived source (canonical) name.
      val recordedNames = callback.classNames.values.flatten.toSet
      assert(recordedNames.contains(("Outer.Inner", "Outer$Inner")))

      // Member-ref deps to the un-loadable class are recorded (Outer references Outer.Inner) ...
      assert(
        callback.classDependencies.contains(("Outer.Inner", "Outer", DependencyByMemberRef))
      )
      // ... as are member-ref deps from it (Outer.Inner references its external superclass pkg.Base).
      assert(
        callback.binaryDependencies.exists {
          case (_, onBinaryName, fromClassName, ctx) =>
            onBinaryName == "pkg.Base" &&
            fromClassName == "Outer.Inner" &&
            ctx == DependencyByMemberRef
        }
      )
      // ... and crucially the inheritance edge that caused the issue (Outer.Inner extends pkg.Base),
      // recovered from the classfile since the class can't be loaded to extract its API.
      assert(
        callback.binaryDependencies.exists {
          case (_, onBinaryName, fromClassName, ctx) =>
            onBinaryName == "pkg.Base" &&
            fromClassName == "Outer.Inner" &&
            ctx == DependencyByInheritance
        }
      )
    }
  }

  // sbt/zinc#837 regression: the unloadable-class fallback must not record module-info.class, which
  // load() deliberately skips (it is not a real class).
  "Analyze" should "not record module-info as a generated class" in {
    IO.withTemporaryDirectory { temp =>
      val classesDir = new File(temp, "classes")
      classesDir.mkdir()

      val moduleInfo = new File(temp, "module-info.java")
      IO.write(moduleInfo, "module foo {}")
      val fooFile = new File(temp, "Foo.java")
      IO.write(fooFile, "package p; public class Foo {}")
      JavaCompilerForUnitTesting.compileJava(Seq(moduleInfo, fooFile), classesDir, Seq.empty)

      val callback = JavaCompilerForUnitTesting.analyze(classesDir, Seq(moduleInfo, fooFile))

      val products = callback.productClassesToSources.keySet.map(_.getFileName.toString)
      assert(products.contains("Foo.class"))
      assert(!products.contains("module-info.class"))

      val recordedNames =
        callback.classNames.values.flatten.toSet.map((p: (String, String)) => p._1)
      assert(!recordedNames.contains("module-info"))
    }
  }

  // sbt/zinc#148: Test only names Foo, but javac needs Foo's whole ancestry present, so it is
  // recorded as member-ref deps of Test.
  "Analyze" should "record the transitive ancestry of a member-referenced type (sbt/zinc#148)" in {
    val srcIBase = "public interface IBase { void base(); }\n"
    val srcIFoo = "public interface IFoo extends IBase { void foo(); }\n"
    val srcBase = "public abstract class Base implements IFoo {}\n"
    val srcFoo =
      """|public class Foo extends Base {
         |  public void base() {}
         |  public void foo() {}
         |  public void other() {}
         |}
         |""".stripMargin
    val srcTest =
      """|public class Test {
         |  void m(Foo f) { f.other(); }
         |}
         |""".stripMargin

    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "IBase.java" -> srcIBase,
      "IFoo.java" -> srcIFoo,
      "Base.java" -> srcBase,
      "Foo.java" -> srcFoo,
      "Test.java" -> srcTest,
    )

    // Recorded as member-ref, not inheritance: Test uses Foo without inheriting from it.
    assert(deps.memberRef("Test") === Set("Foo", "Base", "IFoo", "IBase"))
    assert(deps.inheritance("Test") === Set.empty)

    // The hierarchy is also still tracked as direct-parent inheritance edges on each owner.
    assert(deps.inheritance("Foo") === Set("Base"))
    assert(deps.inheritance("Base") === Set("IFoo"))
    assert(deps.inheritance("IFoo") === Set("IBase"))
  }

  // sbt/zinc#148: the ancestry of a referenced classpath (library) type is recorded too, as
  // binary deps.
  "Analyze" should "record transitive ancestry of a referenced classpath type as binary deps (sbt/zinc#148)" in {
    IO.withTemporaryDirectory { temp =>
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

      def hasBinaryMemberRef(on: String): Boolean =
        callback.binaryDependencies.exists {
          case (_, onName, fromName, ctx) =>
            onName == on && fromName == "Client" && ctx == DependencyByMemberRef
        }

      assert(hasBinaryMemberRef("pkg.Lib")) // the directly referenced type
      assert(hasBinaryMemberRef("pkg.LibBase")) // transitive superclass
      assert(hasBinaryMemberRef("pkg.LibIface")) // transitive interface
    }
  }

  // sbt/zinc#148 regression: javax.* ancestors are ordinary classpath jars, so origin-based (not
  // package-prefix) platform detection must keep tracking them.
  "Analyze" should "track a javax.* ancestor served from the classpath, not the JDK (sbt/zinc#148)" in {
    IO.withTemporaryDirectory { temp =>
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
      assert(callback.binaryDependencies.exists {
        case (_, onName, fromName, ctx) =>
          onName == "javax.foo.Base" && fromName == "Client" && ctx == DependencyByMemberRef
      })
    }
  }

  // issue: sbt/zinc#146 (parameter annotations, JVMS 4.7.18)
  "Analyze" should "detect annotation on method parameter" in {
    val srcTest =
      """|import java.lang.annotation.Retention;
         |import java.lang.annotation.RetentionPolicy;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface Test { }
         |""".stripMargin
    val srcFoo =
      """|public class Foo {
         |  int foo(@Test int x) { return x; }
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  // issue: sbt/zinc#146 (type annotations, JVMS 4.7.20)
  "Analyze" should "detect type-use annotation on a field type" in {
    val srcTest =
      """|import java.lang.annotation.ElementType;
         |import java.lang.annotation.Target;
         |@Target(ElementType.TYPE_USE)
         |public @interface Test { }
         |""".stripMargin
    val srcFoo =
      """|public class Foo {
         |  @Test String s = "";
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  // issue: sbt/zinc#146 (type annotations nested in the Code attribute, JVMS 4.7.3)
  "Analyze" should "detect type-use annotation on a local variable" in {
    val srcTest =
      """|import java.lang.annotation.ElementType;
         |import java.lang.annotation.Target;
         |@Target(ElementType.TYPE_USE)
         |public @interface Test { }
         |""".stripMargin
    val srcFoo =
      """|public class Foo {
         |  void m() {
         |    @Test String s = "";
         |  }
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  // issue: sbt/zinc#146 (annotations on record components, JVMS 4.7.30)
  "Analyze" should "detect annotation on a record component" in {
    val srcTest =
      """|import java.lang.annotation.ElementType;
         |import java.lang.annotation.Target;
         |@Target(ElementType.RECORD_COMPONENT)
         |public @interface Test { }
         |""".stripMargin
    val srcFoo =
      """|public record Foo(@Test int x) { }
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Test"))
  }

  // issue: sbt/zinc#146 (types referenced by an annotation default, JVMS 4.7.22)
  "Analyze" should "detect a type referenced by an annotation default value" in {
    val srcColor =
      """|public enum Color { RED, GREEN }
         |""".stripMargin
    val srcHolder =
      """|public @interface Holder {
         |  Color color() default Color.RED;
         |}
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Color.java" -> srcColor,
      "Holder.java" -> srcHolder,
    )
    assert(deps.memberRef("Holder").contains("Color"))
  }

  // issue: sbt/zinc#146 (class-literal element value stored as an array descriptor `[LBar;`)
  "Analyze" should "detect a type in an array class-literal annotation argument" in {
    val srcTest =
      """|import java.lang.annotation.Retention;
         |import java.lang.annotation.RetentionPolicy;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface Test { Class<?> value(); }
         |""".stripMargin
    val srcBar =
      """|public class Bar { }
         |""".stripMargin
    val srcFoo =
      """|@Test(Bar[].class)
         |public class Foo { }
         |""".stripMargin
    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs(
      "Test.java" -> srcTest,
      "Bar.java" -> srcBar,
      "Foo.java" -> srcFoo,
    )
    assert(deps.memberRef("Foo").contains("Bar"))
  }

}
