package sbt.inc

import sbt.internal.inc.Analysis
import sbt.io.IO

class NestedJavaClassSpec extends BaseCompilerSpec {
  it should "handle nested Java classes" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq("NestedJavaClasses.java"))

      val result = projectSetup.createCompiler().doCompile()
      result.analysis() match {
        case analysis: Analysis =>
          analysis.relations.libraryDep._2s
            .filter(_.toPath.startsWith(tempDir.toPath)) shouldBe 'empty
      }
    }
  }
}
