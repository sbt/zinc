/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc

import java.nio.file.Paths

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
        tempDir.toPath,
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
          projectSetup.update(nahaPath.resolve("naha").resolve(name))(change)
      }

      val result2 =
        compilerSetup.doCompile(_.withPreviousResult(compilerSetup.compiler.previousResult(result)))
      if (changes.isEmpty) {
        assert(!result2.hasModified)
      } else {
        val recompiledUnitsNames =
          compilerSetup.lastCompiledUnits.map(n => Paths.get(n).getFileName.toString)
        recompiledUnitsNames should equal((transitiveChanges ++ changes.map(_._1)).toSet)
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
      transitiveChanges = Set(Other3, Other)
    )
    testIncrementalCompilation(
      changes = Seq(Other -> addNewSealedChildren),
      transitiveChanges = Set(Other3, Other),
      optimizedSealed = true
    )
  }

}
