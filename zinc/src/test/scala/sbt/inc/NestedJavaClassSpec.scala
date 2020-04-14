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

class NestedJavaClassSpec extends BaseCompilerSpec {
  it should "handle nested Java classes" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq("NestedJavaClasses.java"))

      val result = projectSetup.createCompiler().doCompile()
      result.analysis() match {
        case analysis: Analysis =>
          analysis.relations.libraryDep._2s
            .filter(_.id.startsWith(tempDir.toPath.toString)) shouldBe 'empty
      }
    }
  }
}
