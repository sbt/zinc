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
import sbt.internal.inc.{ Analysis, PlainVirtualFile }
import sbt.io.IO
import sbt.io.syntax.*

object BinaryDepSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example("not depend on non-existing objects", nonExistingObjects),
  )

  private def nonExistingObjects: Result = IO.withTemporaryDirectory { tmp =>
    val baseSetup = ProjectSetup.simple(tmp.toPath / "base", Seq("foo/NoopMacro.scala"))
    withCompiler(baseSetup.createCompiler()) { compiler =>
      compiler.doCompileWithStore()
      val proj0 = ProjectSetup.simple(tmp.toPath / "proj", Seq("NoopMacroUsed.scala"))
      val mapping = (baseSetup.classesDir.toFile ** "*.class").get().map { file =>
        file -> baseSetup.classesDir.relativize(file.toPath).toString
      }
      val dest = proj0.baseDir / s"bin/${baseSetup.baseDir.getFileName}.jar"
      IO.zip(mapping, dest.toFile, Some(0L))
      val projectSetup = proj0.copy(
        classPath = proj0.classPath :+ dest,
        analysisForCp = proj0.analysisForCp + (PlainVirtualFile(dest) -> baseSetup.analysisPath)
      )
      withCompiler(projectSetup.createCompiler()) { compiler2 =>
        val a = compiler2.doCompile().analysis.asInstanceOf[Analysis]
        val libraryDeps = a.relations.libraryDep._2s
        // We should not depend on jar creating from project that we depend on (since we've got analysis for it)
        Result.all(
          List(
            libraryDeps.filter(_.id.startsWith(tmp.toPath.toString)) ==== Set.empty,
            libraryDeps.filter(_.id.endsWith("rt.jar")) ==== Set.empty,
          )
        )
      }
    }
  }
end BinaryDepSpec
