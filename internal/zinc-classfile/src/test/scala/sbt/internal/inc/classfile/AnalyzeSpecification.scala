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

  // Step D: under classfileApiOnly there are no loaded classes, so a local/anonymous class's
  // enclosing source must be recovered from the EnclosingMethod attribute — otherwise a dependency
  // referenced only inside the local class body (here `Dep`, used only in the anonymous Runnable)
  // would be dropped, under-compiling. Before the EnclosingMethod mapping this assertion failed.
  "Analyze" should "map local-class member-ref dependencies to the enclosing source under classfileApiOnly" in {
    IO.withTemporaryDirectory { temp =>
      val classesDir = new File(temp, "classes")
      classesDir.mkdir()

      val depFile = new File(temp, "Dep.java")
      IO.write(depFile, "public class Dep {}")
      val holderFile = new File(temp, "Holder.java")
      IO.write(
        holderFile,
        """|public class Holder {
           |  public Runnable make() {
           |    return new Runnable() { public void run() { Dep d = new Dep(); } };
           |  }
           |}
           |""".stripMargin
      )
      JavaCompilerForUnitTesting.compileJava(Seq(depFile, holderFile), classesDir, Seq.empty)

      val callback =
        JavaCompilerForUnitTesting.analyze(
          classesDir,
          Seq(depFile, holderFile),
          classfileApiOnly = true
        )

      // The anonymous class is recorded as a local product (no canonical name).
      val products = callback.productClassesToSources.keySet.map(_.getFileName.toString)
      assert(products.contains("Holder$1.class"))

      // `Dep` is referenced only inside the anonymous class, yet its member-ref dependency is
      // attributed to the enclosing `Holder` source — proving the local→enclosing mapping works
      // without loading any class.
      assert(callback.classDependencies.contains(("Dep", "Holder", DependencyByMemberRef)))
    }
  }

}
