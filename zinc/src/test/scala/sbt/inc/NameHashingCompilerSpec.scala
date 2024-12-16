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

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths }

import sbt.io.IO

class NameHashingCompilerSpec extends BaseCompilerSpec {
  def testIncrementalCompilation(
      changes: Seq[(String, String => String)],
      transitiveChanges: Set[String],
      optimizedSealed: Boolean = true
  ) = {
    val nahaPath = Paths.get("naha")
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup(
        VirtualSubproject(tempDir.toPath),
        Map(nahaPath -> SourceFiles.Naha.all.map(nahaPath.resolve)),
        Nil
      )

      val compilerSetup = {
        val default = projectSetup.createCompiler()
        if (optimizedSealed)
          default.copy(incOptions = default.incOptions.withUseOptimizedSealed(true))
        else default
      }

      val result = compilerSetup.doCompile()

      changes.foreach {
        case (name, change) =>
          val source = nahaPath.resolve("naha").resolve(name)
          val sourceFile = projectSetup.baseDir.resolve(source)
          val text = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8)
          Files.write(sourceFile, change(text).getBytes(StandardCharsets.UTF_8))
      }

      val result2 =
        compilerSetup.doCompile(_.withPreviousResult(compilerSetup.zinc.previousResult(result)))
      if (changes.isEmpty) {
        assert(!result2.hasModified)
      } else {
        val recompiledUnitsNames =
          compilerSetup.lastCompiledUnits.map(n => Paths.get(n).getFileName.toString)

        recompiledUnitsNames should equal((transitiveChanges ++ changes.map(_._1)))
        assert(result2.hasModified)
      }
    }
  }

  def changeImplicitMemberType(in: String) = in.replace("\"implicitMemberValue\"", "42")
  def changeStandardMemberType(in: String) = in.replace("\"standardMemberValue\"", "42")
  def changeOtherSealedType(in: String) =
    in.replace("class OtherSealed", "class OtherSealed extends MarkerTrait")
  def addNewSealedChildren(in: String) =
    s"""
      |$in
      |object OtherSealedNew extends OtherSealed
    """.stripMargin

  import SourceFiles.Naha._

  "incremental compiler" should "not compile anything if sources has not changed" in {
    testIncrementalCompilation(changes = Seq(), transitiveChanges = Set())
  }

  it should "recompile all usages of class on implicit name change" in {
    testIncrementalCompilation(
      changes = Seq(WithImplicits -> changeImplicitMemberType),
      transitiveChanges =
        Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
    )
  }

  it should "not recompile all usages of class on non implicit name change" in {
    testIncrementalCompilation(
      changes = Seq(SourceFiles.Naha.WithImplicits -> changeStandardMemberType),
      transitiveChanges =
        Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
    )
    testIncrementalCompilation(
      changes = Seq(SourceFiles.Naha.NormalDependecy -> changeStandardMemberType),
      transitiveChanges =
        Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
    )
    testIncrementalCompilation(
      changes = Seq(SourceFiles.Naha.NormalDependecy -> changeImplicitMemberType),
      transitiveChanges = Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed)
    )
    testIncrementalCompilation(
      changes = Seq(SourceFiles.Naha.Other -> changeImplicitMemberType),
      transitiveChanges = Set(ClientWithoutAnythingUsed)
    )
    testIncrementalCompilation(
      changes = Seq(SourceFiles.Naha.Other3 -> changeImplicitMemberType),
      transitiveChanges = Set()
    )
  }

  it should "recompile sealed classes correctly" in {
    testIncrementalCompilation(
      changes = Seq(Other -> changeOtherSealedType),
      transitiveChanges = Set(Other2, Other3)
    )
    testIncrementalCompilation(
      changes = Seq(Other -> addNewSealedChildren),
      transitiveChanges = Set(Other, Other2, Other3),
      optimizedSealed = false
    )

    testIncrementalCompilation(
      changes = Seq(Other -> addNewSealedChildren),
      transitiveChanges = Set(Other3, Other),
      optimizedSealed = true
    )
  }

}
