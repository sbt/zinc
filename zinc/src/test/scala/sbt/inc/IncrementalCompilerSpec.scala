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
    example("replace Java products and analysis before deferred compilation", replaceJavaProducts),
    example("retain mixed dependencies after deferred Java compilation", retainMixedDependencies),
    example("skip deferred Java compilation for Scala-only sources", skipJavaForScalaOnly),
    example("roll back prepared Java outputs on cancellation", javaPreparationCancelled),
    example(
      "preserve cached Java bytes and roll back failed deferred compilation",
      javaPreparationRollback
    ),
    example("trigger full compilation if extra changes", extraChanges),
    example("delete all products if extra changes", deleteProductsOnExtraChanges),
    example(
      "not trigger full compilation for small Scala changes in a mixed project",
      smallScalaChange
    ),
    example("track dependencies from nested inner Java classes", nestedInnerJavaClasses),
    example("track dependencies on constants", constants),
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

  private def replaceJavaProducts: Result = withTmpDir: tempDir =>
    withCompiler(VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()): comp =>
      val oldJava = StringVirtualFile(
        "A.java",
        "public class A { public static void main(String[] args) {} static class Old {} Runnable r = new Runnable() { public void run() {} }; }"
      )
      val otherJava = StringVirtualFile("C.java", "public class C {}")
      val first = comp.compileAllJava(oldJava, otherJava).analysis.asInstanceOf[Analysis]
      val newJava = StringVirtualFile("A.java", "public class A { static class Fresh {} }")
      val next = comp.compileAllJava(newJava, otherJava).analysis.asInstanceOf[Analysis]
      Result.all(List(
        assertExists(comp.output / "A.class"),
        assertExists(comp.output / "A$Fresh.class"),
        assertExists(comp.output / "C.class"),
        assertNotExists(comp.output / "A$Old.class"),
        assertNotExists(comp.output / "A$1.class"),
        Result.assert(first.relations.products(oldJava).size > 1),
        Result.assert(!next.relations.products(newJava).exists(_.id.endsWith("A$Old.class"))),
        Result.assert(!next.apis.internal.contains("A.Old")),
        Result.assert(next.infos.allInfos.values.flatMap(_.getMainClasses).isEmpty)
      ))

  private def retainMixedDependencies: Result = withTmpDir: tempDir =>
    withCompiler(VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()): comp =>
      val jf = StringVirtualFile("A.java", "public class A {}")
      val changed = StringVirtualFile("A.java", "public class A extends B {}")
      val scala = StringVirtualFile(
        "B.scala",
        "class B { def a: A = null }; object Main { def main(args: Array[String]): Unit = () }"
      )
      val first = comp.compile(jf, scala).analysis.asInstanceOf[Analysis]
      val bytes = java.nio.file.Files.readAllBytes(comp.output / "B.class").toSeq
      val next = comp.compileAllJava(changed, scala).analysis.asInstanceOf[Analysis]
      val repeated = comp.compileAllJava(changed, scala).analysis.asInstanceOf[Analysis]
      Result.all(List(
        java.nio.file.Files.readAllBytes(comp.output / "B.class").toSeq ==== bytes,
        next.relations.internalClassDeps("A") ==== Set("B"),
        repeated.relations.internalClassDeps("A") ==== Set("B"),
        next.relations.internalClassDeps("B") ==== first.relations.internalClassDeps("B"),
        next.apis.internal("B").apiHash ==== first.apis.internal("B").apiHash,
        next.infos.allInfos(scala).getMainClasses.toSeq ====
          first.infos.allInfos(scala).getMainClasses.toSeq,
        next.stamps.sources(scala) ==== first.stamps.sources(scala)
      ))

  private def skipJavaForScalaOnly: Result = withTmpDir: tempDir =>
    withCompiler(VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()): comp =>
      val sf = StringVirtualFile("B.scala", "class B")
      val first = comp.compile(sf)
      val bytes = java.nio.file.Files.readAllBytes(comp.output / "B.class").toSeq
      val next = comp.compileAllJava(sf)
      Result.all(List(
        Result.assert(!next.hasModified),
        next.analysis.readStamps.getAllSourceStamps ====
          first.analysis.readStamps.getAllSourceStamps,
        java.nio.file.Files.readAllBytes(comp.output / "B.class").toSeq ==== bytes
      ))

  private def javaPreparationCancelled: Result = withTmpDir: tempDir =>
    val setup = VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()
    val options = setup.incOptions.withClassfileManagerType(
      xsbti.compile.TransactionalManagerType.of(tempDir / "backup", sbt.util.Logger.Null)
    )
    withCompiler(setup.copy(incOptions = options)): comp =>
      val jf = StringVirtualFile("A.java", "public class A {}")
      val sf = StringVirtualFile("B.scala", "class B")
      val first = comp.compileBoth(jf, sf)
      val bytes = java.nio.file.Files.readAllBytes(comp.output / "A.class").toSeq
      var prepared = false
      val cancelledJavac = new xsbti.compile.JavaCompiler:
        def run(
            sources: Array[VirtualFile],
            options: Array[String],
            output: Output,
            toolOptions: xsbti.compile.IncToolOptions,
            reporter: xsbti.Reporter,
            log: xsbti.Logger
        ): Boolean =
          prepared = java.nio.file.Files.notExists(comp.output / "A.class") &&
            java.nio.file.Files.exists(comp.output / "B.class")
          throw new xsbti.CompileCancelled:
            def arguments(): Array[String] = Array.empty
      val tools = new xsbti.compile.JavaTools:
        def javac() = cancelledJavac
        def javadoc() = comp.in.compilers.javaTools.javadoc()
      val next = comp.doCompileAllJava(i =>
        i.withOptions(i.options.withSources(Array(jf, sf)))
          .withPreviousResult(comp.zinc.previousResult(first))
          .withCompilers(i.compilers.withJavaTools(tools))
      )
      Result.all(List(
        Result.assert(prepared),
        Result.assert(!next.hasModified),
        Result.assert(next.analysis eq first.analysis),
        java.nio.file.Files.readAllBytes(comp.output / "A.class").toSeq ==== bytes
      ))

  private def javaPreparationRollback: Result = withTmpDir: tempDir =>
    val setup = VirtualSubproject(tempDir.toPath / "sub1").setup.createCompiler()
    val options = setup.incOptions.withClassfileManagerType(
      xsbti.compile.TransactionalManagerType.of(tempDir / "backup", sbt.util.Logger.Null)
    )
    withCompiler(setup.copy(incOptions = options)): comp =>
      val jf = StringVirtualFile("A.java", "public class A { public int value() { return 0; } }")
      val sf = StringVirtualFile("B.scala", "class B")
      comp.compileBoth(jf, sf)
      val storedBytes = java.nio.file.Files.readAllBytes(comp.analysisStoreLocation).toSeq
      val output = comp.output / "A.class"
      val cas = tempDir.toPath / "cached-A.class"
      java.nio.file.Files.move(output, cas)
      java.nio.file.Files.createSymbolicLink(output, cas)
      val cachedBytes = java.nio.file.Files.readAllBytes(cas).toSeq
      val scalaBytes = java.nio.file.Files.readAllBytes(comp.output / "B.class").toSeq
      val broken = StringVirtualFile("A.java", "public class A { this is invalid; }")
      val failure = intercept[xsbti.CompileFailed](comp.compileAllJava(broken, sf))
      val rolledBack = java.nio.file.Files.readAllBytes(output).toSeq
      val stored = java.nio.file.Files.readAllBytes(comp.analysisStoreLocation).toSeq
      val cachedAfterFailure = java.nio.file.Files.readAllBytes(cas).toSeq
      val changed =
        StringVirtualFile("A.java", "public class A { public int value() { return 5; } }")
      val next = comp.compileAllJava(changed, sf).analysis.asInstanceOf[Analysis]
      Result.all(List(
        failure.fold(identity, _ => Result.success),
        rolledBack ==== cachedBytes,
        cachedAfterFailure ==== cachedBytes,
        stored ==== storedBytes,
        java.nio.file.Files.readAllBytes(cas).toSeq ==== cachedBytes,
        java.nio.file.Files.readAllBytes(comp.output / "B.class").toSeq ==== scalaBytes,
        Result.assert(java.nio.file.Files.readAllBytes(output).toSeq != cachedBytes),
        Result.assert(!java.nio.file.Files.isSymbolicLink(output)),
        Result.assert(next.relations.products(changed).nonEmpty)
      ))

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
