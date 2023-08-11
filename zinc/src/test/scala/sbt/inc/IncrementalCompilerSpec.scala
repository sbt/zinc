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

import sbt.internal.inc._
import sbt.io.IO.{ withTemporaryDirectory => withTmpDir }
import sbt.io.syntax._
import xsbti.compile.{ AnalysisStore, CompileAnalysis, DefaultExternalHooks, Inputs, Output }
import java.util.Optional

import xsbti.VirtualFile

class IncrementalCompilerSpec extends BaseCompilerSpec {
  // override val logLevel = sbt.util.Level.Debug
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

  it should "delete orphan class files" in withTmpDir { tempDir =>
    val classes = Seq(SourceFiles.Good, SourceFiles.Foo)
    val comp = ProjectSetup.simple(tempDir.toPath, classes).createCompiler()
    // comp1 is identical to comp, showing stability for identical arguments
    val comp1 = ProjectSetup.simple(tempDir.toPath, classes).createCompiler()
    // Foo2 is same as Foo but in a different package
    val classes2 = Seq(SourceFiles.Good, SourceFiles.Foo2)
    val comp2 = ProjectSetup.simple(tempDir.toPath, classes2).createCompiler()
    try {
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile))

      val result = comp.doCompileWithStore(fileStore)
      assert(result.hasModified)

      val result2 = comp1.doCompileWithStore(fileStore)
      assert(!result2.hasModified)

      // We've changed source files, but also change setup to trigger full compilation
      val setup = comp2.setup.withExtra(Array())
      val result3 = comp2.doCompileWithStore(fileStore, _.withSetup(setup))
      assert(result3.hasModified)

      // TODO should this be more of an "assert exactly the following classes exist?"
      assertExists(comp2.output / "pkg" / "Good$.class")
      assertExists(comp2.output / "pkg2" / "Foo$.class")
      assertNotExists(comp2.output / "pkg" / "Foo$.class")
    } finally comp.close()
  }

  it should "not trigger full compilation for small Scala changes in a mixed project" in withTmpDir {
    tmp =>
      val project = VirtualSubproject(tmp.toPath / "p1")
      val comp = project.setup.createCompiler()
      try {
        val s1 = "class A { def a = 1 }"
        val s1b = "class A { def a = 2 }"
        val s2 = "class B { def b = 1 }"
        val s3 = "class C { def c = 1 }"
        val s4 = "public class D { public int d = 1; }"
        val s5 = "public class E { public int e = 1; }"

        val f1 = StringVirtualFile("A.scala", s1)
        val f1b = StringVirtualFile("A.scala", s1b)
        val f2 = StringVirtualFile("B.scala", s2)
        val f3 = StringVirtualFile("C.scala", s3)
        val f4 = StringVirtualFile("D.java", s4)
        val f5 = StringVirtualFile("E.java", s5)

        comp.compile(f1, f2, f3, f4, f5)
        val result = comp.compile(f1b, f2, f3, f4, f5)
        assert(lastClasses(result.analysis.asInstanceOf[Analysis]) == Set("A", "D", "E"))
      } finally {
        comp.close()
      }
  }

  it should "track dependencies from nested inner Java classes" in withTmpDir { tmp =>
    val project = VirtualSubproject(tmp.toPath / "p1")
    val comp = project.setup.createCompiler()
    try {
      val s1 =
        "public class A { public Object i = new Object() { public Object ii = new Object() { public int i = B.b; }; }; }"
      val s2 = "public class B { public static int b = 1; }"
      val s2b = "public class B { public static int b = 1; public static int b2 = 1; }"
      val s3 = "public class C { public static int c = 1; }"

      val f1 = StringVirtualFile("A.java", s1)
      val f2 = StringVirtualFile("B.java", s2)
      val f2b = StringVirtualFile("B.java", s2b)
      val f3 = StringVirtualFile("C.java", s3)

      def compileJava(sources: VirtualFile*) = {
        def incrementalJavaInputs(sources: VirtualFile*)(in: Inputs): Inputs = {
          comp.withSrcs(sources.toArray)(
            in.withOptions(
              in.options
                .withEarlyOutput(Optional.empty[Output])
                // remove -YpickleXXX args
                .withScalacOptions(comp.scalacOptions.toArray)
            )
              .withSetup(
                in.setup.withIncrementalCompilerOptions(
                  // remove pipelining
                  in.setup.incrementalCompilerOptions.withPipelining(false)
                )
              )
          )
        }
        comp.doCompileWithStore(newInputs = incrementalJavaInputs(sources: _*))
      }

      val res1 = compileJava(f1, f2, f3)
      val res2 = compileJava(f1, f2b, f3)
      assert(recompiled(res1, res2) == Set("A", "B"))
    } finally {
      comp.close()
    }
  }

  it should "track dependencies on constants" in withTmpDir { tmp =>
    val project = VirtualSubproject(tmp.toPath / "p1")
    val comp = project.setup.createCompiler()
    try {
      val s1 = "object A { final val i = 1 }"
      val s1b = "object A { final val i = 2 }"
      val s2 = "class B { def i = A.i }"
      val s3 = "class C { def i = 3 }"

      val f1 = StringVirtualFile("A.scala", s1)
      val f1b = StringVirtualFile("A.scala", s1b)
      val f2 = StringVirtualFile("B.scala", s2)
      val f3 = StringVirtualFile("C.scala", s3)

      val res1 = comp.compile(f1, f2, f3)
      val res2 = comp.compile(f1b, f2, f3)
      assert(recompiled(res1, res2) == Set("A", "B"))
    } finally {
      comp.close()
    }
  }
}
