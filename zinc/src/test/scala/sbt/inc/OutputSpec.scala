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
import java.nio.file.{ Files, Path }
import sbt.io.IO.withTemporaryDirectory as withTmpDir
import sbt.internal.inc.{ CompileFailed, StringVirtualFile, JarUtils }
import sbt.io.syntax.*

class OutputSpec extends BaseCompilerSpec:
  // override val logLevel = sbt.util.Level.Debug
  behavior.of("incremental compiler")

  private val zipTimeResolutionMillis = 2000L

  private val traitM = StringVirtualFile("M.scala", "package p; trait M { def m: Int = 1 }")
  private val classA =
    StringVirtualFile("A.scala", "package p; abstract class A extends M { def a = 1 }")
  private val classAChanged =
    StringVirtualFile("A.scala", "package p; abstract class A extends M { def a = 2 }")
  private val classABroken = StringVirtualFile(
    "A.scala",
    """package p; abstract class A extends M { override def m: String = "" }"""
  )
  private val classB = StringVirtualFile("B.scala", "package p; class B")
  private val classC = StringVirtualFile("C.scala", """package p; class C { val c: Int = "" }""")

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

  for sv <- Seq(scalaVersion, "3.9.0") do
    "early output" should s"not change on a round back to its sources ($sv)" in withTmpDir { tmp =>
      val comp = compiler(sv, tmp.toPath / "p")
      try
        comp.compile(traitM, classA)
        val before = earlyJar(comp, "A", "M")
        comp.compile(traitM, classAChanged)
        comp.compile(traitM, classA)
        assert(earlyJar(comp, "A", "M").sameElements(before))
      finally comp.close()
    }

    it should s"match a fresh build after a class is removed ($sv)" in withTmpDir { tmp =>
      val comp = compiler(sv, tmp.toPath / "p")
      val fresh = compiler(sv, tmp.toPath / "fresh")
      try
        comp.compile(traitM, classA, classB)
        comp.compile(traitM, classA)
        fresh.compile(traitM, classA)
        assert(earlyJar(comp, "A", "M").sameElements(earlyJar(fresh, "A", "M")))
      finally
        comp.close()
        fresh.close()
    }

    it should s"match a fresh build after every source is removed ($sv)" in withTmpDir { tmp =>
      val comp = compiler(sv, tmp.toPath / "p")
      val fresh = compiler(sv, tmp.toPath / "fresh")
      try
        comp.compile(traitM, classA)
        comp.compile()
        fresh.compile()
        assert(earlyJar(comp).sameElements(earlyJar(fresh)))
      finally
        comp.close()
        fresh.close()
    }

    it should s"match a build of the same sources made later ($sv)" in withTmpDir { tmp =>
      def build(dir: String): Array[Byte] =
        val comp = compiler(sv, tmp.toPath / dir)
        try
          comp.compile(traitM, classA)
          earlyJar(comp, "A", "M")
        finally comp.close()
      val first = build("first")
      Thread.sleep(zipTimeResolutionMillis)
      assert(build("second").sameElements(first))
    }

    it should s"be restored after a compile that fails late ($sv)" in withTmpDir { tmp =>
      val comp = compiler(sv, tmp.toPath / "p")
      try
        comp.compile(traitM, classA)
        val before = earlyJar(comp, "A", "M")
        val written = comp.earlyOutputsWritten
        assertThrows[CompileFailed](comp.compile(traitM, classABroken))
        assert(comp.earlyOutputsWritten == written + 1, "the failed compile wrote no early output")
        comp.setup.reporter.reset()
        comp.compile(traitM, classA)
        assert(earlyJar(comp, "A", "M").sameElements(before))
      finally comp.close()
    }

    it should s"be alone after compiles that change nothing or fail early ($sv)" in withTmpDir {
      tmp =>
        val comp = compiler(sv, tmp.toPath / "p")
        try
          comp.compile(traitM, classA)
          val before = earlyJar(comp, "A", "M")
          comp.compile(traitM, classA)
          assert(earlyJar(comp, "A", "M").sameElements(before))
          assertThrows[CompileFailed](comp.compile(traitM, classA, classC))
          assert(earlyJar(comp, "A", "M").sameElements(before))
        finally comp.close()
    }
  end for

  private def compiler(sv: String, dir: Path): CompilerSetup =
    Files.createDirectories(dir)
    VirtualSubproject(dir).setup.createCompiler(sv, incOptions)

  private def earlyJar(comp: CompilerSetup, classes: String*): Array[Byte] =
    val jar = comp.earlyOutput
    assert(jar.getParent.toFile.list.toList == List(jar.getFileName.toString))
    val pickled = JarUtils.listFiles(jar).map(_.stripPrefix("p/").takeWhile(_ != '.'))
    assert(pickled.toSet == classes.toSet)
    Files.readAllBytes(jar)

  def mkCompiler(baseDir: File, classes: Seq[String]) =
    ProjectSetup.simple(baseDir.toPath, classes).copy(outputToJar = true).createCompiler()
end OutputSpec
