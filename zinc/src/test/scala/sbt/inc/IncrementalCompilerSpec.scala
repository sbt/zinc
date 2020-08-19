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

import xsbti.compile.{ AnalysisStore, CompileAnalysis, DefaultExternalHooks }
import sbt.internal.inc._
import sbt.io.IO.{ withTemporaryDirectory => withTmpDir }
import sbt.io.syntax._
import java.util.Optional

class IncrementalCompilerSpec extends BaseCompilerSpec {
  //override val logLevel = sbt.util.Level.Debug
  behavior.of("incremental compiler")

  it should "compile" in withTmpDir { tmp =>
    val comp = ProjectSetup.simple(tmp.toPath, Seq(SourceFiles.Good)).createCompiler()
    try {
      val result = comp.doCompile()
      assertExists(comp.output / "pkg" / "Good$.class")
      assert(!result.analysis.readStamps.getAllSourceStamps.isEmpty)
    } finally comp.close()
  }

  it should "not compile anything if source has not changed" in withTmpDir { tmp =>
    val classes = Seq(SourceFiles.Good, SourceFiles.Foo)
    val comp = ProjectSetup.simple(tmp.toPath, classes).createCompiler()
    try {
      val result = comp.doCompile()
      val result2 = comp.doCompile(_.withPreviousResult(comp.zinc.previousResult(result)))
      assert(!result2.hasModified)
    } finally comp.close()
  }

  it should "honour Lookup#shouldDoEarlyOutput" in withTmpDir { tmp =>
    val ext = new NoopExternalLookup { override def shouldDoEarlyOutput(a: CompileAnalysis) = true }
    val extHooks = new DefaultExternalHooks(Optional.of(ext), Optional.empty())
    def compilerSetupHelper(ps: ProjectSetup) = {
      val setup1 = ps.copy(scalacOptions = ps.scalacOptions :+ "-language:experimental.macros")
      val c1 = setup1.createCompiler()
      c1.copy(incOptions = c1.incOptions.withExternalHooks(extHooks))
    }
    val p1 = VirtualSubproject(tmp.toPath / "p1")
    val p2 = VirtualSubproject(tmp.toPath / "p2").dependsOn(p1)
    val c1 = compilerSetupHelper(p1.setup)
    val c2 = compilerSetupHelper(p2.setup)
    try {
      val s1 = s"object A { def f(c: scala.reflect.macros.blackbox.Context) = c.literalUnit }"
      val s2 = s"object B { def f: Unit = macro A.f }"
      c1.compile(StringVirtualFile("A.scala", s1))
      c2.compile(StringVirtualFile("B.scala", s2))
      assertExists(c1.earlyOutput)
      assertExists(c2.earlyOutput)
    } finally {
      c1.close()
      c2.close()
    }
  }

  it should "compile Java code" in withTmpDir { tmp =>
    val comp = ProjectSetup.simple(tmp.toPath, Seq(SourceFiles.NestedJavaClasses)).createCompiler()
    try {
      comp.doCompileWithStore()
      val result1 = comp.doCompileAllJavaWithStore()
      assertExists(comp.output / "NestedJavaClasses.class")
      assert(!result1.analysis.readStamps.getAllSourceStamps.isEmpty)
    } finally comp.close()
  }

  it should "compile all Java code" in withTmpDir { tempDir =>
    val c1 = VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()
    try {
      val result = c1.compileAllJava(StringVirtualFile("A.java", "public class A {}"))
      assert(!result.analysis.readStamps.getAllSourceStamps.isEmpty)
      assertExists(c1.output / "A.class")
    } finally c1.close()
  }

  it should "compile all Java code in a mixed project" in withTmpDir { tempDir =>
    val c1 = VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()
    try {
      val jf = StringVirtualFile("A.java", "public class A {}")
      val sf = StringVirtualFile("B.scala", "class B { val a = new A }")

      val result1 = c1.compile(jf, sf)
      assert(result1.analysis.readStamps.getAllSourceStamps.keySet.size == 2)

      val result2 = c1.compileAllJava(jf, sf)
      assert(!result2.analysis.readStamps.getAllSourceStamps.isEmpty)
      assertExists(c1.output / "A.class")
    } finally c1.close()
  }

  it should "trigger full compilation if extra changes" in withTmpDir { tempDir =>
    val classes = Seq(SourceFiles.Good, SourceFiles.Foo)
    val comp = ProjectSetup.simple(tempDir.toPath, classes).createCompiler()
    try {
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile))

      val result = comp.doCompileWithStore(fileStore)
      assert(result.hasModified)

      val result2 = comp.doCompileWithStore(fileStore)
      assert(!result2.hasModified)

      val setup = comp.setup.withExtra(Array())
      val result3 = comp.doCompileWithStore(fileStore, _.withSetup(setup))
      assert(result3.hasModified)
    } finally comp.close()
  }
}
