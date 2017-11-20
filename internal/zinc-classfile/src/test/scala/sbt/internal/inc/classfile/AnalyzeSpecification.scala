/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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

    val deps = JavaCompilerForUnitTesting.extractDependenciesFromSrcs("A.java" -> srcA,
                                                                      "C.java" -> srcC,
                                                                      "D.java" -> srcD)

    assert(deps.memberRef("A") === Set("A.B"))
    assert(deps.memberRef("A.B") === Set("A", "D"))
    assert(deps.memberRef("C") === Set("A", "A.B"))
    assert(deps.memberRef("D") === Set.empty)
  }

}
