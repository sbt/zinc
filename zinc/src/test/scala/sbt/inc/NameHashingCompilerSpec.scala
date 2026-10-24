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
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths }

import sbt.io.IO

object NameHashingCompilerSpec extends Properties with BaseCompilerSpec:
  import SourceFiles.Naha.*

  override def tests: List[Test] = List(
    example(
      "incremental compiler should not compile anything if sources has not changed",
      testIncrementalCompilation(changes = Seq(), transitiveChanges = Set())
    ),
    example(
      "recompile all usages of class on implicit name change",
      testIncrementalCompilation(
        changes = Seq(WithImplicits -> changeImplicitMemberType),
        transitiveChanges =
          Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
      )
    ),
    example(
      "not recompile all usages of class on non implicit name change",
      Result.all(
        List(
          testIncrementalCompilation(
            changes = Seq(WithImplicits -> changeStandardMemberType),
            transitiveChanges =
              Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
          ),
          testIncrementalCompilation(
            changes = Seq(NormalDependecy -> changeStandardMemberType),
            transitiveChanges =
              Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
          ),
          testIncrementalCompilation(
            changes = Seq(NormalDependecy -> changeImplicitMemberType),
            transitiveChanges = Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed)
          ),
          testIncrementalCompilation(
            changes = Seq(Other -> changeImplicitMemberType),
            transitiveChanges = Set(ClientWithoutAnythingUsed)
          ),
          testIncrementalCompilation(
            changes = Seq(Other3 -> changeImplicitMemberType),
            transitiveChanges = Set()
          ),
        )
      )
    ),
    example(
      "recompile sealed classes correctly",
      Result.all(
        List(
          testIncrementalCompilation(
            changes = Seq(Other -> changeOtherSealedType),
            transitiveChanges = Set(Other2, Other3)
          ),
          testIncrementalCompilation(
            changes = Seq(Other -> addNewSealedChildren),
            transitiveChanges = Set(Other, Other2, Other3),
            optimizedSealed = false
          ),
          testIncrementalCompilation(
            changes = Seq(Other -> addNewSealedChildren),
            transitiveChanges = Set(Other3, Other),
            optimizedSealed = true
          ),
        )
      )
    ),
  )

  def testIncrementalCompilation(
      changes: Seq[(String, String => String)],
      transitiveChanges: Set[String],
      optimizedSealed: Boolean = true
  ): Result =
    val nahaPath = Paths.get("naha")
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup(
        VirtualSubproject(tempDir.toPath),
        Map(nahaPath -> SourceFiles.Naha.all.map(nahaPath.resolve)),
        Nil
      )
      val compilerSetup =
        val default = projectSetup.createCompiler()
        if optimizedSealed then
          default.copy(incOptions = default.incOptions.withUseOptimizedSealed(true))
        else default
      withCompiler(compilerSetup) { compilerSetup =>
        val result = compilerSetup.doCompile()
        changes.foreach { (name, change) =>
          val sourceFile = projectSetup.baseDir.resolve(nahaPath.resolve("naha").resolve(name))
          val text = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8)
          Files.write(sourceFile, change(text).getBytes(StandardCharsets.UTF_8))
        }
        val result2 =
          compilerSetup.doCompile(_.withPreviousResult(compilerSetup.zinc.previousResult(result)))
        if changes.isEmpty then Result.assert(!result2.hasModified)
        else
          val recompiledUnitsNames =
            compilerSetup.lastCompiledUnits.map(n => Paths.get(n).getFileName.toString)
          Result.all(
            List(
              recompiledUnitsNames ==== (transitiveChanges ++ changes.map(_._1)),
              Result.assert(result2.hasModified),
            )
          )
      }
    }
  end testIncrementalCompilation

  def changeImplicitMemberType(in: String) = in.replace("\"implicitMemberValue\"", "42")
  def changeStandardMemberType(in: String) = in.replace("\"standardMemberValue\"", "42")
  def changeOtherSealedType(in: String) =
    in.replace("class OtherSealed", "class OtherSealed extends MarkerTrait")
  def addNewSealedChildren(in: String) =
    s"""
      |$in
      |object OtherSealedNew extends OtherSealed
    """.stripMargin
end NameHashingCompilerSpec
