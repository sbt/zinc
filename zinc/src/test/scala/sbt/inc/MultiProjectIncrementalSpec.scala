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

import java.nio.file.{ Files, Path, StandardCopyOption }

import sbt.internal.inc._
import sbt.io.IO
import TestResource._
import xsbti.compile.CompileResult

class MultiProjectIncrementalSpec extends BaseCompilerSpec {
  //override def logLevel = sbt.util.Level.Debug

  "incremental compiler" should "detect shadowing" in {
    IO.withTemporaryDirectory { tempDir =>
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

      val c1 = p1.setup.createCompiler()
      val c2 = p2.setup.createCompiler()

      def assertExists(p: Path) = assert(Files.exists(p), s"$p does not exist")
      try {
        // This registers `test.pkg.Ext1` as the class name on the binary stamp
        c1.compile(c1.toVf(dependerFile))
        assertExists(c1.output / "test/pkg/Depender$.class")
        assertExists(c1.earlyOutput)

        // This registers `test.pkg.Ext2` as the class name on the binary stamp,
        // which means `test.pkg.Ext1` is no longer in the stamp.
        c1.compile(c1.toVf(dependerFile), depender2File)
        assertExists(c1.output / "test/pkg/Depender2$.class")
        assertExists(c1.earlyOutput)

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
        val result3 = c1.compile(
          c1.toVf(knownSampleGoodFile),
          c1.toVf(dependerFile),
          depender2File,
          depender3File
        )
        val a3 = result3.analysis.asInstanceOf[Analysis]
        c1.compileAllJava(
          c1.toVf(knownSampleGoodFile),
          c1.toVf(dependerFile),
          depender2File,
          depender3File
        )

        // Depender.scala should be invalidated since it depends on test.pkg.Ext1 from the JAR file,
        // but the class is now shadowed by sub2/target.
        assert(lastClasses(a3).contains("test.pkg.Depender"))
      } finally {
        c1.close()
        c2.close()
      }
    }
  }

  "it" should "not compile Java for no-op" in (IO.withTemporaryDirectory { tmp =>
    val p1 = VirtualSubproject(tmp.toPath / "sub1")
    val p2 = VirtualSubproject(tmp.toPath / "sub2").dependsOn(p1)
    val c1 = p1.setup.createCompiler()
    val c2 = p2.setup.createCompiler()
    try {
      val s1 = "package pkg; class A"
      val s2 = "package pkg; class B { def a = new A }"
      val s3 = "package pkg; public class Z { public static A x = new B().a(); }"
      val s4 = "package pkg; public class Z { public static A y = new B().a(); }"

      val f1 = StringVirtualFile("A.scala", s1)
      val f2 = StringVirtualFile("B.scala", s2)
      val f3 = StringVirtualFile("Z.java", s3)
      val f4 = StringVirtualFile("Z.java", s4)

      c1.compile(f1)
      val result = c2.compileBoth(f2, f3)
      assert(startTimes(result) == startTimes(c2.compileBoth(f2, f3)))
      assert(startTimes(result) != startTimes(c2.compileBoth(f2, f4)))
    } finally {
      c1.close()
      c2.close()
    }
  })

  "it" should "recompile Java on upstream changes" in (IO.withTemporaryDirectory { tmp =>
    val p1 = VirtualSubproject(tmp.toPath / "sub1")
    val p2 = VirtualSubproject(tmp.toPath / "sub2").dependsOn(p1)
    val c1 = p1.setup.createCompiler()
    val c2 = p2.setup.createCompiler()
    try {
      val s1 = "package pkg; class A { def a = 1 }"
      val s1b = "package pkg; class A { def a1 = 1 }"
      val s2 = "package pkg; class B extends A { def b = 2 }"
      val s3 = "package pkg; class C"
      val s4 = "package pkg; public class Z { public static int x = 3; }"

      val f1 = StringVirtualFile("A.scala", s1)
      val f1b = StringVirtualFile("A.scala", s1b)
      val f2 = StringVirtualFile("B.scala", s2)
      val f3 = StringVirtualFile("C.scala", s3)
      val f4 = StringVirtualFile("Z.java", s4)

      c1.compile(f1)
      c2.compile(f2, f3, f4)
      c1.compile(f1b)
      val result = c2.compile(f2, f3, f4)
      assert(lastClasses(result.analysis.asInstanceOf[Analysis]) == Set("pkg.B", "pkg.Z"))
    } finally {
      c1.close()
      c2.close()
    }
  })

  def lastClasses(a: Analysis) = {
    a.compilations.allCompilations.map { c =>
      a.apis.internal.collect {
        case (className, api) if api.compilationTimestamp == c.getStartTime => className
      }.toSet
    }.last
  }

  def startTimes(res: CompileResult) = {
    res.analysis.readCompilations.getAllCompilations.map(_.getStartTime).toList
  }
}

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
