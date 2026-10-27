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
import java.io.File
import java.nio.file.{ Files, Path }
import sbt.io.IO.withTemporaryDirectory as withTmpDir
import sbt.internal.inc.{ CompileFailed, HedgehogSupport, StringVirtualFile, JarUtils }
import sbt.io.syntax.*
import HedgehogSupport.assertThrows

object OutputSpec extends Properties with BaseCompilerSpec:
  // override val logLevel = sbt.util.Level.Debug
  override def tests: List[Test] =
    List(
      example("incremental compiler should compile directly to JAR", compileToJar),
      example("not compile anything if source has not changed", noChange),
      example("generate early artifact with pickle (.sig) files", earlyArtifact),
    ) ++ List(scalaVersion, "3.9.0").flatMap(earlyOutputTests)

  private def earlyOutputTests(sv: String): List[Test] = List(
    example(s"early output should not change on a round back to its sources ($sv)", roundBack(sv)),
    example(s"match a fresh build after a class is removed ($sv)", classRemoved(sv)),
    example(s"match a fresh build after every source is removed ($sv)", allRemoved(sv)),
    example(s"match a build of the same sources made later ($sv)", madeLater(sv)),
    example(s"be restored after a compile that fails late ($sv)", failsLate(sv)),
    example(s"be alone after compiles that change nothing or fail early ($sv)", failsEarly(sv)),
  )

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

  private def compileToJar: Result = withTmpDir { baseDir =>
    withCompiler(mkCompiler(baseDir, Seq(SourceFiles.Good))) { compiler =>
      val result = compiler.doCompile()
      Result.all(
        List(
          assertExists(compiler.output),
          Result.assert(!result.analysis.readStamps.getAllSourceStamps.keySet.isEmpty),
        )
      )
    }
  }

  private def noChange: Result = withTmpDir { baseDir =>
    withCompiler(mkCompiler(baseDir, Seq(SourceFiles.Good, SourceFiles.Foo))) { compiler =>
      val result = compiler.doCompileWithStore()
      val result2 = compiler.doCompileWithStore()
      Result.all(
        List(
          Result.assert(!result.analysis.readStamps.getAllSourceStamps.keySet.isEmpty),
          Result.assert(!result2.hasModified),
        )
      )
    }
  }

  private def earlyArtifact: Result = withTmpDir { baseDir =>
    withCompiler(VirtualSubproject(baseDir.toPath).setup.createCompiler()) { c1 =>
      c1.compile(StringVirtualFile("src/example/A.scala", "package example; class A"))
      Result.all(
        List(
          assertExists(c1.earlyOutput),
          Result.assert(JarUtils.listFiles(c1.earlyOutput).contains("example/A.sig")),
        )
      )
    }
  }

  private def roundBack(sv: String): Result = withTmpDir { tmp =>
    withCompiler(compiler(sv, tmp.toPath / "p")) { comp =>
      comp.compile(traitM, classA)
      val before = earlyJar(comp, "A", "M")
      comp.compile(traitM, classAChanged)
      comp.compile(traitM, classA)
      sameJar(earlyJar(comp, "A", "M"), before)
    }
  }

  private def classRemoved(sv: String): Result = withTmpDir { tmp =>
    withCompiler(compiler(sv, tmp.toPath / "p")) { comp =>
      withCompiler(compiler(sv, tmp.toPath / "fresh")) { fresh =>
        comp.compile(traitM, classA, classB)
        comp.compile(traitM, classA)
        fresh.compile(traitM, classA)
        sameJar(earlyJar(comp, "A", "M"), earlyJar(fresh, "A", "M"))
      }
    }
  }

  private def allRemoved(sv: String): Result = withTmpDir { tmp =>
    withCompiler(compiler(sv, tmp.toPath / "p")) { comp =>
      withCompiler(compiler(sv, tmp.toPath / "fresh")) { fresh =>
        comp.compile(traitM, classA)
        comp.compile()
        fresh.compile()
        sameJar(earlyJar(comp), earlyJar(fresh))
      }
    }
  }

  private def madeLater(sv: String): Result = withTmpDir { tmp =>
    def build(dir: String): EarlyJar =
      val comp = compiler(sv, tmp.toPath / dir)
      try
        comp.compile(traitM, classA)
        earlyJar(comp, "A", "M")
      finally comp.close()
    val first = build("first")
    Thread.sleep(zipTimeResolutionMillis)
    sameJar(build("second"), first)
  }

  private def failsLate(sv: String): Result = withTmpDir { tmp =>
    withCompiler(compiler(sv, tmp.toPath / "p")) { comp =>
      comp.compile(traitM, classA)
      val before = earlyJar(comp, "A", "M")
      val written = comp.earlyOutputsWritten
      val failed = assertThrows[CompileFailed](comp.compile(traitM, classABroken))
      val wroteOnce = (comp.earlyOutputsWritten ==== written + 1)
        .log("the failed compile wrote no early output")
      comp.setup.reporter.reset()
      comp.compile(traitM, classA)
      Result.all(List(failed, wroteOnce, sameJar(earlyJar(comp, "A", "M"), before)))
    }
  }

  private def failsEarly(sv: String): Result = withTmpDir { tmp =>
    withCompiler(compiler(sv, tmp.toPath / "p")) { comp =>
      comp.compile(traitM, classA)
      val before = earlyJar(comp, "A", "M")
      comp.compile(traitM, classA)
      val afterNoop = earlyJar(comp, "A", "M")
      val failed = assertThrows[CompileFailed](comp.compile(traitM, classA, classC))
      Result.all(
        List(sameJar(afterNoop, before), failed, sameJar(earlyJar(comp, "A", "M"), before))
      )
    }
  }

  private def compiler(sv: String, dir: Path): CompilerSetup =
    Files.createDirectories(dir)
    VirtualSubproject(dir).setup.createCompiler(sv, incOptions)

  /** The bytes of an early output, with the checks that it is alone and holds `classes`. */
  private case class EarlyJar(bytes: Array[Byte], checks: Result)

  private def earlyJar(comp: CompilerSetup, classes: String*): EarlyJar =
    val jar = comp.earlyOutput
    val pickled = JarUtils.listFiles(jar).map(_.stripPrefix("p/").takeWhile(_ != '.'))
    EarlyJar(
      Files.readAllBytes(jar),
      Result.all(
        List(
          jar.getParent.toFile.list.toList ==== List(jar.getFileName.toString),
          pickled.toSet ==== classes.toSet,
        )
      )
    )

  private def sameJar(actual: EarlyJar, expected: EarlyJar): Result =
    Result.all(
      List(
        actual.checks,
        expected.checks,
        Result.assert(actual.bytes.sameElements(expected.bytes)).log("the early outputs differ"),
      )
    )

  def mkCompiler(baseDir: File, classes: Seq[String]) =
    ProjectSetup.simple(baseDir.toPath, classes).copy(outputToJar = true).createCompiler()
end OutputSpec
