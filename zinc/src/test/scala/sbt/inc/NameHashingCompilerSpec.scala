package sbt.inc

import java.nio.file.Paths

import sbt.io.IO

/**
 * Author: Krzysztof Romanowski
 */
class NameHashingCompilerSpec extends BaseCompilerSpec {

  def testCompilation(changes: Seq[(String, String => String)], transitiveChanges: Set[String]) = {
    val nahaPath = Paths.get("naha")
    IO.withTemporaryDirectory { tempDir =>
      val projectSetup = ProjectSetup(
        tempDir.toPath,
        Map(nahaPath -> SourceFiles.Naha.all.map(nahaPath.resolve)),
        Nil
      )

      val compilerSetup = projectSetup.createCompiler()

      val result = compilerSetup.doCompile()

      changes.foreach {
        case (name, change) =>
          projectSetup.update(nahaPath.resolve("naha").resolve(name))(change)
      }

      val result2 = compilerSetup.doCompile(_.withPreviousResult(compilerSetup.compiler.previousResult(result)))
      if (changes.isEmpty) {
        assert(!result2.hasModified)
      } else {
        val recompiledUnitsNames = compilerSetup.lastCompiledUnits.map(n => Paths.get(n).getFileName.toString)
        recompiledUnitsNames should equal((transitiveChanges ++ changes.map(_._1)).toSet)
        assert(result2.hasModified)
      }
    }
  }

  def changeImplicitMemberType(in: String) = in.replace("\"implicitMemberValue\"", "42")
  def changeStandardMemberType(in: String) = in.replace("\"standardMemberValue\"", "42")
  def changeOtherSealedType(in: String) = in.replace("class OtherSealed", "class OtherSealed extends MarkerTrait")
  def addNewSealedChildren(in: String) =
    s"""
      |$in
      |object OtherSealedNew extends OtherSealed
    """.stripMargin

  import SourceFiles.Naha._

  "incremental compiler" should "not compile anything if sources has not changed" in {
    testCompilation(changes = Seq(), transitiveChanges = Set())
  }

  it should "recompile all usages of class on implicit name change" in {
    testCompilation(
      changes = Seq(WithImplicits -> changeImplicitMemberType),
      transitiveChanges = Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
    )
  }

  it should "not recompile all usages of class on non implicit name change" in {
    testCompilation(
      changes = Seq(SourceFiles.Naha.WithImplicits -> changeStandardMemberType),
      transitiveChanges = Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
    )
    testCompilation(
      changes = Seq(SourceFiles.Naha.NormalDependecy -> changeStandardMemberType),
      transitiveChanges = Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed, ClientWithoutImplicit)
    )
    testCompilation(
      changes = Seq(SourceFiles.Naha.NormalDependecy -> changeImplicitMemberType),
      transitiveChanges = Set(ClientWithImplicitUsed, ClientWithImplicitNotUsed)
    )
    testCompilation(
      changes = Seq(SourceFiles.Naha.Other -> changeImplicitMemberType),
      transitiveChanges = Set(ClientWithoutAnythingUsed)
    )
    testCompilation(
      changes = Seq(SourceFiles.Naha.Other3 -> changeImplicitMemberType),
      transitiveChanges = Set()
    )
  }

  it should "recompile sealed classes correctly" in {
    testCompilation(
      changes = Seq(Other -> changeOtherSealedType),
      transitiveChanges = Set(Other2, Other3)
    )
    testCompilation(
      changes = Seq(Other -> addNewSealedChildren),
      transitiveChanges = Set(Other3)
    )
  }

}
