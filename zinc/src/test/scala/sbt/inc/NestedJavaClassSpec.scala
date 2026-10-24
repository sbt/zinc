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

package sbt.inc

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.Analysis
import sbt.io.IO

object NestedJavaClassSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example("handle nested Java classes", nestedJavaClasses),
  )

  private def nestedJavaClasses: Result = IO.withTemporaryDirectory { tempDir =>
    val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq("NestedJavaClasses.java"))
    withCompiler(projectSetup.createCompiler()) { compiler =>
      compiler.doCompile().analysis() match
        case analysis: Analysis =>
          analysis.relations.libraryDep._2s.filter(_.id.startsWith(tempDir.toPath.toString)) ====
            Set.empty
        case other => Result.failure.log(s"unexpected analysis: $other")
    }
  }
end NestedJavaClassSpec
