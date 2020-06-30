/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.inc

import sbt.internal.inc.Analysis
import sbt.io.IO

class BinaryDepSpec extends BaseCompilerSpec {
  it should "not depend on non-existing objects" in {
    IO.withTemporaryDirectory { tempDir =>
      val basePath = tempDir.toPath.resolve("base")
      val baseSetup = ProjectSetup.simple(basePath, Seq("foo/NoopMacro.scala"))
      val compiler = baseSetup.createCompiler()
      try {
        compiler.doCompileWithStore()

        val projPath = tempDir.toPath.resolve("proj")
        val projectSetup =
          ProjectSetup.simple(projPath, Seq("NoopMacroUsed.scala")).dependsOnJarFrom(baseSetup)

        val compiler2 = projectSetup.createCompiler()
        try {
          val result = compiler2.doCompile()
          result.analysis() match {
            case analysis: Analysis =>
              // We should not depend on jar creating from project that we depend on (since we've got analysis for it)
              analysis.relations.libraryDep._2s
                .filter(_.id.startsWith(tempDir.toPath.toString)) shouldBe 'empty

              analysis.relations.libraryDep._2s
                .filter(_.name == "rt.jar") shouldBe 'empty
          }
        } finally {
          compiler2.close()
        }
      } finally {
        compiler.close()
      }
    }
  }
}
