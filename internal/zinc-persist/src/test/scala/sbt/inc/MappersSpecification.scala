package sbt.inc

import java.util

import org.scalacheck.Properties
import org.scalacheck._
import Gen._
import Prop._
import sbt.internal.inc.{ EnumSetSerializer, Mapper, TestCaseGenerators, UsedName }
import xsbti.UseScope

class MappersSpecification extends Properties("TextAnalysisFormat") {

  private def checkName(name: UsedName) =
    name =? Mapper.forUsedName.read(Mapper.forUsedName.write(name))

  property("Maps UsedName correctly") =
    forAll(TestCaseGenerators.genUsedName())(checkName)

  property("Maps UsedName correctly for specific names") =
    forAll(TestCaseGenerators.genUsedName(Gen.oneOf(Seq("::"))))(checkName)

  property("Maps EnumSet correctly") = {
    val gen = Gen.someOf(UseScope.values()).map(UsedName("a", _)).map(_.scopes)
    val serizlizer = EnumSetSerializer(UseScope.values())

    def check(useScopes: util.EnumSet[UseScope]) = useScopes =? serizlizer.deserialize(serizlizer.serialize(useScopes))

    forAll(gen)(check)
  }
}
