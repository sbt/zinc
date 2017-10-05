package sbt.inc

import sbt.internal.inc.Analysis
import sbt.io.IO

class BinaryDepSpec extends BaseCompilerSpec {
  it should "not depend on non-existing objects" in {
    IO.withTemporaryDirectory { tempDir =>
      val basePath = tempDir.toPath.resolve("base")
      val baseSetup = ProjectSetup.simple(basePath, Seq("foo/NoopMacro.scala"))
      baseSetup.createCompiler().doCompileWithStore()

      val projPath = tempDir.toPath.resolve("proj")
      val projectSetup =
        ProjectSetup.simple(projPath, Seq("NoopMacroUsed.scala")).dependsOnJarFrom(baseSetup)

      val result = projectSetup.createCompiler().doCompile()
      result.analysis() match {
        case analysis: Analysis =>
          // We should not depend on jar creating from project that we depend on (since we've got analysis for it)
          analysis.relations.libraryDep._2s
            .filter(_.toPath.startsWith(tempDir.toPath)) shouldBe 'empty

      }
    }
  }
}
