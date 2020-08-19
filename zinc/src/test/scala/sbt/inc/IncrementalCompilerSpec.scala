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
import sbt.io.IO
import sbt.io.syntax._
import java.nio.file.Files
import java.util.Optional

class IncrementalCompilerSpec extends BaseCompilerSpec {
  //override val logLevel = sbt.util.Level.Debug

  "incremental compiler" should "compile" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good))
      val compiler = projectSetup.createCompiler()
      try {
        val result = projectSetup.createCompiler().doCompile()
        val expectedOuts =
          List(projectSetup.defaultClassesDir.resolve("pkg").resolve("Good$.class"))
        expectedOuts foreach { f =>
          assert(Files.exists(f), s"$f does not exist.")
        }
        val a = result.analysis match {
          case a: Analysis => a
        }
        assert(a.stamps.allSources.nonEmpty)
      } finally {
        compiler.close()
      }
    }
  }

  it should "not compile anything if source has not changed" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()
      try {

        val result = compilerSetup.doCompile()
        val result2 =
          compilerSetup.doCompile(
            _.withPreviousResult(compilerSetup.compiler.previousResult(result))
          )

        assert(!result2.hasModified)
      } finally {
        compilerSetup.close()
      }
    }
  }

  it should "honour Lookup#shouldDoEarlyOutput" in IO.withTemporaryDirectory { tmp =>
    val ext = new NoopExternalLookup { override def shouldDoEarlyOutput(a: CompileAnalysis) = true }
    val extHooks = new DefaultExternalHooks(Optional.of(ext), Optional.empty())
    implicit val compilerSetupHelper = new TestProjectSetup.CompilerSetupHelper {
      override def apply(sv: String, ps: TestProjectSetup) = {
        val setup1 = ps.copy(scalacOptions = ps.scalacOptions :+ "-language:experimental.macros")
        val c1 = setup1.createCompiler(sv)
        c1.copy(incOptions = c1.incOptions.withExternalHooks(extHooks))
      }
    }
    val p1 = VirtualSubproject.Builder().baseDirectory(tmp.toPath / "p1").get
    val p2 = VirtualSubproject.Builder().baseDirectory(tmp.toPath / "p2").dependsOn(p1).get
    try {
      val s1 = s"object A { def f(c: scala.reflect.macros.blackbox.Context) = c.literalUnit }"
      val s2 = s"object B { def f: Unit = macro A.f }"
      p1.compile(StringVirtualFile("A.scala", s1))
      p2.compile(StringVirtualFile("B.scala", s2))
      assertExists(p1.setup.earlyOutput)
      assertExists(p2.setup.earlyOutput)
    } finally {
      p1.close()
      p2.close()
    }
  }

  it should "compile Java code" in {
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.NestedJavaClasses))

      val compiler = projectSetup.createCompiler()
      try {
        compiler.doCompileWithStore()
        val result1 = compiler.doCompileAllJavaWithStore()
        val expectedOuts = List(projectSetup.defaultClassesDir.resolve("NestedJavaClasses.class"))
        expectedOuts foreach { f =>
          assert(Files.exists(f), s"$f does not exist.")
        }
        val a = result1.analysis match {
          case a: Analysis => a
        }
        assert(a.stamps.allSources.nonEmpty)
      } finally {
        compiler.close()
      }
    }
  }

  it should "compile all Java code" in {
    IO.withTemporaryDirectory { tempDir =>
      val sub1Directory = tempDir.toPath / "sub1"
      Files.createDirectories(sub1Directory / "src")
      val p1 = VirtualSubproject
        .Builder()
        .baseDirectory(sub1Directory)
        .get

      try {
        val javaContent =
          """package example;
          |
          |public class A {
          |}
          |""".stripMargin
        val javaFile = StringVirtualFile("src/example/A.java", javaContent)
        val result = p1.compileAllJava(javaFile)
        val a = result.analysis match {
          case a: Analysis => a
        }
        assert(a.stamps.allSources.nonEmpty)
        assertExists(sub1Directory / "classes" / "example" / "A.class")
      } finally p1.close()
    }
  }

  it should "compile all Java code in a mixed project" in {
    IO.withTemporaryDirectory { tempDir =>
      val sub1Directory = tempDir.toPath / "sub1"
      Files.createDirectories(sub1Directory / "src")
      Files.createDirectories(sub1Directory / "classes")
      val p1 = VirtualSubproject
        .Builder()
        .baseDirectory(sub1Directory)
        .get

      try {
        val javaContent =
          """package example;
          |
          |public class A {
          |}
          |""".stripMargin
        val javaFile = StringVirtualFile("src/example/A.java", javaContent)

        val scalaContent =
          """package example
          |
          |class B {
          |  val a = new A
          |}
          |""".stripMargin
        val scalaFile = StringVirtualFile("src/example/B.scala", scalaContent)
        val result1 = p1.compile(javaFile, scalaFile)
        val a1 = result1.analysis.asInstanceOf[Analysis]
        assert(a1.stamps.allSources.size == 2)
        // assert(!Files.exists(sub1Directory / "classes" / "example" / "A.class"))
        val result2 = p1.compileAllJava(javaFile, scalaFile)
        val a2 = result2.analysis.asInstanceOf[Analysis]
        assert(a2.stamps.allSources.nonEmpty)
        assertExists(sub1Directory / "classes" / "example" / "A.class")
      } finally p1.close()
    }
  }

  it should "trigger full compilation if extra changes" in {
    IO.withTemporaryDirectory { tempDir =>
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore0 = FileAnalysisStore.binary(cacheFile)
      val fileStore = AnalysisStore.getCachedStore(fileStore0)

      val projectSetup =
        ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo))
      val compilerSetup = projectSetup.createCompiler()

      val result = compilerSetup.doCompileWithStore(fileStore)
      assert(result.hasModified)

      val result2 = compilerSetup.doCompileWithStore(fileStore)
      assert(!result2.hasModified)

      val result3 =
        compilerSetup.doCompileWithStore(
          fileStore,
          _.withSetup(compilerSetup.setup.withExtra(Array()))
        )
      assert(result3.hasModified)
    }
  }
}
