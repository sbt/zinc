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
import java.nio.file.{ Files, StandardCopyOption }

import sbt.internal.inc.*
import sbt.io.IO
import TestResource.*

object MultiProjectIncrementalSpec extends Properties with BaseCompilerSpec:
  // override def logLevel = sbt.util.Level.Debug
  override def tests: List[Test] = List(
    example("incremental compiler should detect shadowing", detectShadowing),
    example("accept a -Ypickle-write path given as a virtual file id", virtualPickleWrite),
    example("not compile Java for no-op", noOpJava),
    example("recompile Java on upstream changes", upstreamChanges),
    example("allow shadowing of dependency classes", shadowDependencyClasses),
  )

  private def detectShadowing: Result = IO.withTemporaryDirectory { tempDir =>
    // Second subproject
    val sub2Directory = tempDir.toPath / "sub2"
    Files.createDirectories(sub2Directory / "src")

    // Prepare the initial compilation
    val sub1Directory = tempDir.toPath / "sub1"
    Files.createDirectories(sub1Directory / "src")
    Files.createDirectories(sub1Directory / "lib")
    val dependerFile = sub1Directory / "src" / "Depender.scala"
    Files.copy(dependerFile0, dependerFile, StandardCopyOption.REPLACE_EXISTING)
    val depender2Content =
      """package test.pkg
        |
        |object Depender2 {
        |  val x = test.pkg.Ext2.x
        |}
        |""".stripMargin
    val depender2File = StringVirtualFile("src/Depender2.scala", depender2Content)
    val binarySampleFile = sub1Directory / "lib" / "sample-binary_2.12-0.1.jar"
    Files.copy(binarySampleFile0, binarySampleFile, StandardCopyOption.REPLACE_EXISTING)
    val p2 = VirtualSubproject(sub2Directory)
    val p1 = VirtualSubproject(sub1Directory).dependsOn(p2).extDeps(binarySampleFile)
    withCompiler(p1.setup.createCompiler()) { c1 =>
      withCompiler(p2.setup.createCompiler()) { c2 =>
        // This registers `test.pkg.Ext1` as the class name on the binary stamp
        c1.compile(c1.toVf(dependerFile))
        val first = List(
          assertExists(c1.output / "test/pkg/Depender$.class"),
          assertExists(c1.earlyOutput),
        )
        // This registers `test.pkg.Ext2` as the class name on the binary stamp,
        // which means `test.pkg.Ext1` is no longer in the stamp.
        c1.compile(c1.toVf(dependerFile), depender2File)
        val second = List(
          assertExists(c1.output / "test/pkg/Depender2$.class"),
          assertExists(c1.earlyOutput),
        )
        // Second subproject
        val ext1File = sub2Directory / "src" / "Ext1.scala"
        Files.copy(ext1File0, ext1File, StandardCopyOption.REPLACE_EXISTING)
        c2.compile(c2.toVf(ext1File))
        // Actual test
        val knownSampleGoodFile = sub1Directory / "src" / "Good.scala"
        Files.copy(knownSampleGoodFile0, knownSampleGoodFile, StandardCopyOption.REPLACE_EXISTING)
        val depender3File = StringVirtualFile(
          "src/Depender3.java",
          """package test.pkg;
            |
            |public class Depender3 {
            |  public static Ext1 x = new Ext1();
            |}""".stripMargin
        )
        val sources =
          Seq(c1.toVf(knownSampleGoodFile), c1.toVf(dependerFile), depender2File, depender3File)
        val a3 = c1.compile(sources*).analysis.asInstanceOf[Analysis]
        c1.compileAllJava(sources*)
        // Depender.scala should be invalidated since it depends on test.pkg.Ext1 from the JAR file,
        // but the class is now shadowed by sub2/target.
        val shadowed = Result.assert(lastClasses(a3).contains("test.pkg.Depender"))
        Result.all(first ++ second :+ shadowed)
      }
    }
  }

  private def virtualPickleWrite: Result = IO.withTemporaryDirectory { tmp =>
    withCompiler(VirtualSubproject(tmp.toPath / "sub1").setup.createCompiler()) { c1 =>
      val earlyId = c1.toVf(c1.earlyOutput).id
      val machinePath = c1.earlyOutput.toString
      val f1 = StringVirtualFile("A.scala", "package pkg; class A")
      c1.doCompileWithStore(newInputs = in =>
        val opts = in.options
        val scalacOpts = opts.scalacOptions.map(o => if o == machinePath then earlyId else o)
        in.withOptions(opts.withScalacOptions(scalacOpts).withSources(Array(f1))))
      Result.all(
        List(
          Result.assert(earlyId.startsWith("${BASE}/")).log(earlyId),
          assertExists(c1.earlyOutput),
        )
      )
    }
  }

  private def noOpJava: Result = IO.withTemporaryDirectory { tmp =>
    val p1 = VirtualSubproject(tmp.toPath / "sub1")
    val p2 = VirtualSubproject(tmp.toPath / "sub2").dependsOn(p1)
    withCompiler(p1.setup.createCompiler()) { c1 =>
      withCompiler(p2.setup.createCompiler()) { c2 =>
        val f1 = StringVirtualFile("A.scala", "package pkg; class A")
        val f2 = StringVirtualFile("B.scala", "package pkg; class B { def a = new A }")
        val f3 =
          StringVirtualFile(
            "Z.java",
            "package pkg; public class Z { public static A x = new B().a(); }"
          )
        val f4 =
          StringVirtualFile(
            "Z.java",
            "package pkg; public class Z { public static A y = new B().a(); }"
          )
        c1.compile(f1)
        c2.compileBoth(f2, f3)
        val noopResult = c2.compileBoth(f2, f3)
        // comparing startTime is no longer a good test

        // comparing startTime is no longer a good test
        val changedResult = c2.compileBoth(f2, f4)
        Result.all(
          List(Result.assert(!noopResult.hasModified()), Result.assert(changedResult.hasModified()))
        )
      }
    }
  }

  private def upstreamChanges: Result = IO.withTemporaryDirectory { tmp =>
    val p1 = VirtualSubproject(tmp.toPath / "sub1")
    val p2 = VirtualSubproject(tmp.toPath / "sub2").dependsOn(p1)
    withCompiler(p1.setup.createCompiler()) { c1 =>
      withCompiler(p2.setup.createCompiler()) { c2 =>
        val f1 = StringVirtualFile("A.scala", "package pkg; class A { def a = 1 }")
        val f1b = StringVirtualFile("A.scala", "package pkg; class A { def a1 = 1 }")
        val f2 = StringVirtualFile("B.scala", "package pkg; class B extends A { def b = 2 }")
        val f3 = StringVirtualFile("C.scala", "package pkg; class C")
        val f4 =
          StringVirtualFile("Z.java", "package pkg; public class Z { public static int x = 3; }")
        c1.compile(f1)
        c2.compile(f2, f3, f4)
        c1.compile(f1b)
        val result = c2.compile(f2, f3, f4)
        lastClasses(result.analysis.asInstanceOf[Analysis]) ==== Set("pkg.B", "pkg.Z")
      }
    }
  }

  private def shadowDependencyClasses: Result = IO.withTemporaryDirectory { tmp =>
    val p1 = VirtualSubproject(tmp.toPath / "sub1")
    val p2 = VirtualSubproject(tmp.toPath / "sub2").dependsOn(p1)
    withCompiler(p1.setup.createCompiler()) { c1 =>
      withCompiler(p2.setup.copy(outputToJar = true).createCompiler()) { c2 =>
        val s1Shadow =
          "package pkg; public class A { public static int a = 1; public class InnerA { public int i = 2; } }"
        c1.compileAllJava(StringVirtualFile(
          "A.java",
          "package pkg; public class A { public static int a = 1; }"
        ))
        // this will fail with "pkg.A and pkg.A$InnerA disagree on InnerClasses attribute" if f1Shadow ends up
        // later on the AnalyzingJavaCompiler.searchClasspath than f1
        c2.compileAllJava(StringVirtualFile("A.java", s1Shadow))
        Result.success
      }
    }
  }
end MultiProjectIncrementalSpec

/* Make a jar with the following:

package test.pkg

object Ext1 {
  val x = 1
}

object Ext2 {
  val x = 2
}

object Ext3 {
  val x = 3
}

object Ext4 {
  val x = 4
}

object Ext5 {
  val x = 5
}

object Ext6 {
  val x = 6
}

object Ext7 {
  val x = 7
}

object Ext8 {
  val x = 8
}

object Ext9 {
  val x = 9
}

object Ext10 {
  val x = 10
}

 */
