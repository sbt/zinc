/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File

import sbt.internal.inc.classfile.JavaCompilerForUnitTesting
import xsbti.AnalysisCallback
import xsbti.api.{ ClassLike, ClassLikeDef, DefinitionType }

class ClassToAPISpecification extends UnitSpec {

  "ClassToAPI" should "extract api of inner classes" in {
    val src =
      """|class A {
        |  class B {}
        |}
      """.stripMargin
    val apis = extractApisFromSrc("A.java" -> src).map(c => c.name -> c).toMap
    assert(apis.keySet === Set("A", "A.B"))

    val companionsA = apis("A")
    assert(companionsA.classApi.topLevel === true)
    assert(companionsA.objectApi.topLevel === true)

    val innerClassDefB =
      findDeclaredInnerClass(companionsA.classApi, "A.B", DefinitionType.ClassDef)
    assert(innerClassDefB.isDefined)

    val companionsB = apis("A.B")
    assert(companionsB.classApi.topLevel === false)
    assert(companionsB.objectApi.topLevel === false)
    assert(companionsB.classApi.structure.declared.isEmpty === false)
  }

  it should "extract a private inner class" in {
    val src =
      """|class A {
        |  private class B {}
        |}
      """.stripMargin
    val apis = extractApisFromSrc("A.java" -> src).map(c => c.name -> c).toMap
    assert(apis.keySet === Set("A", "A.B"))
  }

  /**
   * Compiles given source code using Java compiler and returns API representation
   * extracted by ClassToAPI class.
   */
  private def extractApisFromSrc(src: (String, String)): Set[Companions] = {
    val (Seq(tempSrcFile), analysisCallback) =
      JavaCompilerForUnitTesting.compileJavaSrcs(src)(readAPI)
    val apis = analysisCallback.apis(tempSrcFile)
    apis.groupBy(_.name).map((companions _).tupled).toSet
  }

  private def companions(className: String, classes: Set[ClassLike]): Companions = {
    assert(classes.size <= 2, s"Too many classes named $className: $classes")
    def isClass(c: ClassLike) =
      (c.definitionType == DefinitionType.Trait) || (c.definitionType == DefinitionType.ClassDef)
    def isModule(c: ClassLike) =
      (c.definitionType == DefinitionType.Module) || (c.definitionType == DefinitionType.PackageModule)
    // the ClassToAPI always create both class and object APIs
    val classApi = classes.find(isClass).get
    val objectApi = classes.find(isModule).get
    Companions(className, classApi, objectApi)
  }

  private case class Companions(name: String, classApi: ClassLike, objectApi: ClassLike)

  private def findDeclaredInnerClass(classApi: ClassLike,
                                     innerClassName: String,
                                     defType: DefinitionType): Option[ClassLikeDef] = {
    classApi.structure.declared.collectFirst({
      case c: ClassLikeDef if c.name == innerClassName && c.definitionType == defType => c
    })
  }

  def readAPI(callback: AnalysisCallback,
              source: File,
              classes: Seq[Class[_]]): Set[(String, String)] = {
    val (apis, mainClasses, inherits) = ClassToAPI.process(classes)
    apis.foreach(callback.api(source, _))
    mainClasses.foreach(callback.mainClass(source, _))
    inherits.map {
      case (from, to) => (from.getName, to.getName)
    }
  }

}
