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

}
