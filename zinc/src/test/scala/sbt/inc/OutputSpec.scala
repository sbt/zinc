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

import java.io.File
import java.nio.file.Files
import java.util.Optional
import sbt.io.IO.{ withTemporaryDirectory => withTmpDir }
import sbt.internal.inc.{ StringVirtualFile, JarUtils }
import xsbti.CompileFailed
import xsbti.compile.{ AnalysisContents, AnalysisStore }

class OutputSpec extends BaseCompilerSpec {
  // override val logLevel = sbt.util.Level.Debug
  behavior.of("incremental compiler")

  it should "compile directly to JAR" in withTmpDir { baseDir =>
    val compiler = mkCompiler(baseDir, Seq(SourceFiles.Good))
    val result = compiler.doCompile()
    assert(Files.exists(compiler.output), s"${compiler.output} does not exist.")
    assert(!result.analysis.readStamps.getAllSourceStamps.keySet.isEmpty)
  }

  it should "not compile anything if source has not changed" in withTmpDir { baseDir =>
    val compiler = mkCompiler(baseDir, Seq(SourceFiles.Good, SourceFiles.Foo))

    val result = compiler.doCompileWithStore()
    assert(!result.analysis.readStamps.getAllSourceStamps.keySet.isEmpty)

    val result2 = compiler.doCompileWithStore()
    assert(!result2.hasModified)
  }

  it should "generate early artifact with pickle (.sig) files" in withTmpDir { baseDir =>
    val c1 = VirtualSubproject(baseDir.toPath).setup.createCompiler()
    c1.compile(StringVirtualFile("src/example/A.scala", "package example; class A"))
    assert(Files.exists(c1.earlyOutput))
    assert(JarUtils.listFiles(c1.earlyOutput).contains("example/A.sig"))
  }

  it should "write early analysis to a store that cannot be read" in withTmpDir { baseDir =>
    val c1 = VirtualSubproject(baseDir.toPath).setup.createCompiler()
    val store = new WriteOnlyAnalysisStore
    def compile(src: String) =
      c1.doCompile(in =>
        c1.withSrcs(Array(StringVirtualFile("src/example/A.scala", src)))(
          in.withSetup(in.setup.withEarlyAnalysisStore(store))
        )
      )
    compile("package example; class A")
    assert(store.sets == 1)
    assert(JarUtils.listFiles(c1.earlyOutput).contains("example/A.sig"))

    // fails in refchecks, after the early output is written
    assertThrows[CompileFailed] {
      compile("package example; class A; class B { def f = 1 }; class C extends B { def f = 2 }")
    }
    assert(JarUtils.listFiles(c1.earlyOutput).contains("example/A.sig"))
    assert(!JarUtils.listFiles(c1.earlyOutput).contains("example/C.sig"))
  }

  def mkCompiler(baseDir: File, classes: Seq[String]) =
    ProjectSetup.simple(baseDir.toPath, classes).copy(outputToJar = true).createCompiler()

  final class WriteOnlyAnalysisStore extends AnalysisStore {
    var sets = 0
    override def get(): Optional[AnalysisContents] = throw new UnsupportedOperationException
    override def unsafeGet(): AnalysisContents = throw new UnsupportedOperationException
    override def set(contents: AnalysisContents): Unit = sets += 1
  }
}
