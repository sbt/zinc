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
import java.nio.file.{ Files, Path }
import java.security.MessageDigest
import java.util.HexFormat
import scala.jdk.CollectionConverters.*

import sbt.internal.inc.consistent.ConsistentFileAnalysisStore
import sbt.io.IO
import xsbti.compile.AnalysisContents
import xsbti.compile.analysis.ReadWriteMappers

object AnalysisPortabilitySpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example("the stored analysis does not depend on the build directory", portable),
  )

  private val warning = "package p\n\nobject Warn {\n  def f: Int = {\n    1\n    2\n  }\n}\n"

  private def digest(base: Path)(k: (String, Int) => Result): Result =
    IO.write(base.resolve("Warn.scala").toFile, warning)
    val setup = ProjectSetup.simple(base, Seq(SourceFiles.Foo, "Warn.scala"))
    withCompiler(setup.createCompiler()) { compiler =>
      val result = compiler.doCompile(in => in.withOptions(in.options.withScalacOptions(Array())))
      val file = base.resolve("inc_compile.zip")
      ConsistentFileAnalysisStore
        .binary(file.toFile, ReadWriteMappers.getEmptyMappers, reproducible = true)
        .set(AnalysisContents.create(result.analysis, result.setup))
      val sha = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
      val problems = result.analysis.readSourceInfos.getAllSourceInfos.values.asScala
        .map(i => i.getReportedProblems.length + i.getUnreportedProblems.length)
        .sum
      k(HexFormat.of.formatHex(sha), problems)
    }

  private def portable: Result =
    IO.withTemporaryDirectory { dir1 =>
      IO.withTemporaryDirectory { dir2 =>
        digest(dir1.toPath.resolve("one")) { (d1, problems) =>
          digest(dir2.toPath.resolve("a-longer-build-directory")) { (d2, _) =>
            Result.all(List(Result.assert(problems > 0), d1 ==== d2)).log(s"$d1 $d2")
          }
        }
      }
    }
end AnalysisPortabilitySpec
