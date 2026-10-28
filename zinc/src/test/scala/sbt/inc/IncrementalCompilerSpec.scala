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
import sbt.internal.inc.*
import sbt.io.IO.withTemporaryDirectory as withTmpDir
import sbt.io.syntax.*
import xsbti.compile.{ AnalysisStore, CompileAnalysis, DefaultExternalHooks, Inputs, Output }
import java.util.Optional
import xsbti.VirtualFile
import HedgehogSupport.{ assertThrows, intercept }

object IncrementalCompilerSpec extends Properties with BaseCompilerSpec:
  // override val logLevel = sbt.util.Level.Debug
  override def tests: List[Test] = List(
    example("incremental compiler should compile", compiles),
    example("not compile anything if source has not changed", noChange),
    example("honour Lookup#shouldDoEarlyOutput", shouldDoEarlyOutput),
    example("compile Java code", compileJava),
    example("compile all Java code", compileAllJava),
    example("compile all Java code in a mixed project", compileAllJavaMixed),
    example("trigger full compilation if extra changes", extraChanges),
    example("delete all products if extra changes", deleteProductsOnExtraChanges),
    example(
      "not trigger full compilation for small Scala changes in a mixed project",
      smallScalaChange
    ),
    example("track dependencies from nested inner Java classes", nestedInnerJavaClasses),
    example("track dependencies on constants", constants),
    example("still fail after retrying when the error is genuine", genuineErrorAfterRetry),
    example("announce early output at most once per compile", earlyOutputOnce),
    example("not throw NullPointerException when passing -Xshow-phases to scalac", showPhases),
    example("emit SourceInfos when incremental compilation fails", sourceInfos),
  )

  private def compiles: Result = withTmpDir { tmp =>
    withCompiler(ProjectSetup.simple(tmp.toPath, Seq(SourceFiles.Good)).createCompiler()) { comp =>
      val result = comp.doCompile()
      Result.all(
        List(
          assertExists(comp.output / "pkg" / "Good$.class"),
          Result.assert(!result.analysis.readStamps.getAllSourceStamps.isEmpty),
        )
      )
    }
  }

  private def noChange: Result = withTmpDir { tmp =>
    val classes = Seq(SourceFiles.Good, SourceFiles.Foo)
    withCompiler(ProjectSetup.simple(tmp.toPath, classes).createCompiler()) { comp =>
      val result = comp.doCompile()
      val result2 = comp.doCompile(_.withPreviousResult(comp.zinc.previousResult(result)))
      Result.assert(!result2.hasModified)
    }
  }

  private def shouldDoEarlyOutput: Result = withTmpDir { tmp =>
    val ext = new NoopExternalLookup:
      override def shouldDoEarlyOutput(a: CompileAnalysis) = true
    val extHooks = new DefaultExternalHooks(Optional.of(ext), Optional.empty())
    def compilerSetupHelper(ps: ProjectSetup) =
      val setup1 = ps.copy(scalacOptions = ps.scalacOptions :+ "-language:experimental.macros")
      val c1 = setup1.createCompiler()
      c1.copy(incOptions = c1.incOptions.withExternalHooks(extHooks))
    val p1 = VirtualSubproject(tmp.toPath / "p1")
    val p2 = VirtualSubproject(tmp.toPath / "p2").dependsOn(p1)
    withCompiler(compilerSetupHelper(p1.setup)) { c1 =>
      withCompiler(compilerSetupHelper(p2.setup)) { c2 =>
        val s1 = "object A { def f(c: scala.reflect.macros.blackbox.Context) = c.literalUnit }"
        val s2 = "object B { def f: Unit = macro A.f }"
        c1.compile(StringVirtualFile("A.scala", s1))
        c2.compile(StringVirtualFile("B.scala", s2))
        Result.all(List(assertExists(c1.earlyOutput), assertExists(c2.earlyOutput)))
      }
    }
  }

  private def compileJava: Result = withTmpDir { tmp =>
    val setup = ProjectSetup.simple(tmp.toPath, Seq(SourceFiles.NestedJavaClasses))
    withCompiler(setup.createCompiler()) { comp =>
      comp.doCompileWithStore()
      val result1 = comp.doCompileAllJavaWithStore()
      Result.all(
        List(
          assertExists(comp.output / "NestedJavaClasses.class"),
          Result.assert(!result1.analysis.readStamps.getAllSourceStamps.isEmpty),
        )
      )
    }
  }

  private def compileAllJava: Result = withTmpDir { tempDir =>
    withCompiler(VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()) { c1 =>
      val result = c1.compileAllJava(StringVirtualFile("A.java", "public class A {}"))
      Result.all(
        List(
          Result.assert(!result.analysis.readStamps.getAllSourceStamps.isEmpty),
          assertExists(c1.output / "A.class"),
        )
      )
    }
  }

  private def compileAllJavaMixed: Result = withTmpDir { tempDir =>
    withCompiler(VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()) { c1 =>
      val jf = StringVirtualFile("A.java", "public class A {}")
      val sf = StringVirtualFile("B.scala", "class B { val a = new A }")
      val result1 = c1.compile(jf, sf)
      val result2 = c1.compileAllJava(jf, sf)
      Result.all(
        List(
          result1.analysis.readStamps.getAllSourceStamps.keySet.size ==== 2,
          Result.assert(!result2.analysis.readStamps.getAllSourceStamps.isEmpty),
          assertExists(c1.output / "A.class"),
        )
      )
    }
  }

  private def extraChanges: Result = withTmpDir { tempDir =>
    val classes = Seq(SourceFiles.Good, SourceFiles.Foo)
    withCompiler(ProjectSetup.simple(tempDir.toPath, classes).createCompiler()) { comp =>
      val cacheFile = tempDir / "target" / "inc_compile.zip"
      val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile))
      val result = comp.doCompileWithStore(fileStore)
      val result2 = comp.doCompileWithStore(fileStore)
      val setup = comp.setup.withExtra(Array())
      val result3 = comp.doCompileWithStore(fileStore, _.withSetup(setup))
      Result.all(
        List(
          Result.assert(result.hasModified),
          Result.assert(!result2.hasModified),
          Result.assert(result3.hasModified),
        )
      )
    }
  }

  private def deleteProductsOnExtraChanges: Result = withTmpDir { tempDir =>
    val comp =
      ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Good, SourceFiles.Foo)).createCompiler()
    withCompiler(comp) { comp =>
      withCompiler(ProjectSetup.simple(tempDir.toPath, Seq(SourceFiles.Foo)).createCompiler()) {
        comp2 =>
          val cacheFile = tempDir / "target" / "inc_compile.zip"
          val fileStore = AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile))
          comp.doCompileWithStore(fileStore)
          val newSetup = comp2.setup.withExtra(Array())
          comp2.doCompileWithStore(fileStore, _.withSetup(newSetup))
          assertNotExists(comp.output / "pkg" / "Good$.class")
      }
    }
  }

  private def smallScalaChange: Result = withTmpDir { tmp =>
    withCompiler(VirtualSubproject(tmp.toPath / "p1").setup.createCompiler()) { comp =>
      val f1 = StringVirtualFile("A.scala", "class A { def a = 1 }")
      val f1b = StringVirtualFile("A.scala", "class A { def a = 2 }")
      val f2 = StringVirtualFile("B.scala", "class B { def b = 1 }")
      val f3 = StringVirtualFile("C.scala", "class C { def c = 1 }")
      val f4 = StringVirtualFile("D.java", "public class D { public int d = 1; }")
      val f5 = StringVirtualFile("E.java", "public class E { public int e = 1; }")
      comp.compile(f1, f2, f3, f4, f5)
      val result = comp.compile(f1b, f2, f3, f4, f5)
      lastClasses(result.analysis.asInstanceOf[Analysis]) ==== Set("A", "D", "E")
    }
  }

  private def nestedInnerJavaClasses: Result = withTmpDir { tmp =>
    withCompiler(VirtualSubproject(tmp.toPath / "p1").setup.createCompiler()) { comp =>
      val s1 =
        "public class A { public Object i = new Object() { public Object ii = new Object() { public int i = B.b; }; }; }"
      val f1 = StringVirtualFile("A.java", s1)
      val f2 = StringVirtualFile("B.java", "public class B { public static int b = 1; }")
      val f2b = StringVirtualFile(
        "B.java",
        "public class B { public static int b = 1; public static int b2 = 1; }"
      )
      val f3 = StringVirtualFile("C.java", "public class C { public static int c = 1; }")

      def compileJava(sources: VirtualFile*) =
        def incrementalJavaInputs(in: Inputs): Inputs =
          comp.withSrcs(sources.toArray)(
            in.withOptions(
              in.options
                .withEarlyOutput(Optional.empty[Output])
                // remove -YpickleXXX args
                .withScalacOptions(comp.scalacOptions.toArray)
            ).withSetup(
              in.setup.withIncrementalCompilerOptions(
                // remove pipelining
                in.setup.incrementalCompilerOptions.withPipelining(false)
              )
            )
          )
        comp.doCompileWithStore(newInputs = incrementalJavaInputs)

      val res1 = compileJava(f1, f2, f3)
      val res2 = compileJava(f1, f2b, f3)
      recompiled(res1, res2) ==== Set("A", "B")
    }
  }

  private def constants: Result = withTmpDir { tmp =>
    withCompiler(VirtualSubproject(tmp.toPath / "p1").setup.createCompiler()) { comp =>
      val f1 = StringVirtualFile("A.scala", "object A { final val i = 1 }")
      val f1b = StringVirtualFile("A.scala", "object A { final val i = 2 }")
      val f2 = StringVirtualFile("B.scala", "class B { def i = A.i }")
      val f3 = StringVirtualFile("C.scala", "class C { def i = 3 }")
      val res1 = comp.compile(f1, f2, f3)
      val res2 = comp.compile(f1b, f2, f3)
      recompiled(res1, res2) ==== Set("A", "B")
    }
  }

  /**
   * sbt/zinc#476: Bar and Providers change together. Foo sits between them, and its class file
   * still has the dealiased old return type, so Bar compiles only once Foo is rebuilt.
   */
  private val issue476Sources = Seq(
    StringVirtualFile("A.scala", "trait A { def a = 1 }"),
    StringVirtualFile("B.scala", "trait B { def b = 1.0 }"),
    StringVirtualFile(
      "Providers.scala",
      "trait Provider { type Operations = A }; object Providers { type SomeProvider = Provider }"
    ),
    StringVirtualFile(
      "Foo.scala",
      "object Foo { def provide: Providers.type#SomeProvider#Operations = ??? }"
    ),
    StringVirtualFile("Bar.scala", "object Bar { Foo.provide.a }"),
  )
  private val issue476Changed = Seq(
    StringVirtualFile(
      "Providers.scala",
      "trait Provider { type Operations = B }; object Providers { type SomeProvider = Provider }"
    ),
    StringVirtualFile("Bar.scala", "object Bar { Foo.provide.b }"),
  )
  private def issue476Edit(sources: Seq[VirtualFile]) =
    sources.map(s => issue476Changed.find(_.id == s.id).getOrElse(s))

  private def genuineErrorAfterRetry: Result = withTmpDir { tmp =>
    withCompiler(VirtualSubproject(tmp.toPath / "p1").setup.createCompiler()) { comp =>
      val bogus = StringVirtualFile("Bar.scala", "object Bar { Foo.provide.nope }")
      comp.compile(issue476Sources*)
      intercept[CompileFailed] {
        comp.compile(issue476Edit(issue476Sources).map(s => if s.id == bogus.id then bogus else s)*)
      } match
        case Left(failure) => failure
        case Right(e)      =>
          val messages = e.problems.toList.map(_.message)
          Result
            .assert(messages.exists(_.contains("value nope is not a member of B")))
            .log(s"expected the error from the retry, against the new alias B: $messages")
    }
  }

  private def earlyOutputOnce: Result = withTmpDir { tmp =>
    withCompiler(VirtualSubproject(tmp.toPath / "p1").setup.createCompiler()) { comp =>
      val a = StringVirtualFile("A.scala", "class A { def b: B = new B }")
      val b = StringVirtualFile("B.scala", "class B extends A")
      val b2 = StringVirtualFile("B.scala", "class B extends A { override def nope = 1 }")
      comp.compile(a, b)
      val before = comp.earlyOutputsWritten
      Result.all(
        List(
          assertThrows[CompileFailed](comp.compile(a, b2)),
          Result
            .assert(comp.earlyOutputsWritten - before <= 1)
            .log(s"early output announced ${comp.earlyOutputsWritten - before} times"),
        )
      )
    }
  }

  private def showPhases: Result = withTmpDir { tmp =>
    val setup = ProjectSetup.simple(tmp.toPath, Seq(SourceFiles.Good), Seq("-Xshow-phases"))
    withCompiler(setup.createCompiler()) { comp =>
      assertThrows[CompileFailed](comp.doCompile())
    }
  }

  private def sourceInfos: Result = withTmpDir { tmp =>
    withCompiler(VirtualSubproject(tmp.toPath / "p1").setup.createCompiler()) { comp =>
      intercept[CompileFailed](comp.compile(StringVirtualFile(
        "A.scala",
        "object A { final val i = 1"
      ))) match
        case Left(failure)    => failure
        case Right(exception) =>
          exception.sourceInfosOption match
            case Some(sourceInfos) =>
              Result.assert(
                !sourceInfos.getAllSourceInfos.isEmpty
              ).log("Expected non-empty source infos")
            case None => Result.failure.log("Expected sourceInfos")
    }
  }
end IncrementalCompilerSpec
