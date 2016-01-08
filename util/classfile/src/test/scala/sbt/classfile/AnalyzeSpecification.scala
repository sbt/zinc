package sbt.classfile

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AnalyzeSpecification extends Specification {

  "dependencies of inner classes" in {
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

    val deps = JavaCompilerForUnitTesting.
      extractDependenciesFromSrcs("A.java" -> srcA, "C.java" -> srcC, "D.java" -> srcD)

    deps.memberRef("A") === Set("A.B")
    deps.memberRef("A.B") === Set("A", "D")
    deps.memberRef("C") === Set("A", "A.B")
    deps.memberRef("D") === Set.empty
  }

}
