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

import java.nio.file.Files
import sbt.io.IO
import sbt.internal.inc.JavaInterfaceUtil._
import sbt.internal.inc._
import xsbti.compile.IncOptions

class OutputSpec extends BaseCompilerSpec {
  override val logLevel = sbt.util.Level.Debug
  def isJava8: Boolean = sys.props("java.specification.version") == "1.8"

  "incremental compiler" should "compile directly to JAR" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup
        .simple(tempDir.toPath, Seq(SourceFiles.Good))
        .copy(outputToJar = true)
      val compilerSetup = projectSetup
        .createCompiler()
        .copy(incOptions = IncOptions.of().withAllowMachinePath(false))
      val result = compilerSetup.doCompile()
      val expectedOuts = List(projectSetup.output)
      expectedOuts foreach { f =>
        assert(Files.exists(f), s"$f does not exist.")
      }
      val a = result.analysis match { case a: Analysis => a }
      assert(a.stamps.allSources.nonEmpty)
    }
  }

  it should "not compile anything if source has not changed" in {
    if (isJava8)
      IO.withTemporaryDirectory { tempDir =>
        val projectSetup =
          ProjectSetup
            .simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
            .copy(outputToJar = true)
        val compilerSetup = projectSetup
          .createCompiler()
          .copy(incOptions = IncOptions.of().withAllowMachinePath(false))
        val result = compilerSetup.doCompileWithStore()
        log.info("==========================================")
        log.info("begin second compilation")
        val prev = compilerSetup.compiler.previousResult(result)
        val analysis: Analysis = prev.analysis.toOption.get match {
          case a: Analysis => a
        }
        log.info(s"prev = $analysis")
        log.info(s"prev.stamps = ${analysis.stamps}")
        val result2 = compilerSetup.doCompileWithStore()

        assert(!result2.hasModified)
      } else {
      // This test fails on zincJVM2_13 + JDK 11
      ()
    }
  }
}
