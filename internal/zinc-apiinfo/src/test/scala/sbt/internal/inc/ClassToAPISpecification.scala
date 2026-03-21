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

package sbt
package internal
package inc

import java.io.File
import sbt.internal.inc.classfile.JavaCompilerForUnitTesting
import sbt.io.IO
import xsbti.{ AnalysisCallback, VirtualFileRef }
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

  // sbt/sbt#117
  it should "not throw NoClassDefFoundError when inner class references missing type" in {
    IO.withTemporaryDirectory { temp =>
      val libDir = new File(temp, "lib")
      val srcDir = new File(temp, "src")
      libDir.mkdir()
      srcDir.mkdir()

      val missingFile = new File(temp, "Missing.java")
      IO.write(missingFile, "public class Missing {}")
      compileJava(Seq(missingFile), libDir, Seq.empty)

      val outerFile = new File(temp, "Outer.java")
      IO.write(
        outerFile,
        """|public class Outer {
           |  public class Inner extends Missing {}
           |  public void hello() {}
           |}
           |""".stripMargin
      )
      compileJava(Seq(outerFile), srcDir, Seq(libDir))

      val classloader = new java.net.URLClassLoader(Array(srcDir.toURI.toURL), null)
      val outerClass = classloader.loadClass("Outer")
      val (apis, _, _) = ClassToAPI.process(Seq(outerClass))

      val names = apis.map(_.name).toSet
      assert(names.contains("Outer"))
      assert(!names.contains("Outer.Inner"))
    }
  }

  private def compileJava(files: Seq[File], outputDir: File, classpath: Seq[File]): Unit = {
    import javax.tools.{ StandardLocation, ToolProvider }
    import scala.jdk.CollectionConverters._
    val compiler = ToolProvider.getSystemJavaCompiler()
    val fileManager = compiler.getStandardFileManager(null, null, null)
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Seq(outputDir).asJava)
    if (classpath.nonEmpty)
      fileManager.setLocation(StandardLocation.CLASS_PATH, classpath.asJava)
    val units = fileManager.getJavaFileObjectsFromFiles(files.asJava)
    compiler.getTask(null, fileManager, null, null, null, units).call()
    fileManager.close()
  }

  /**
   * Compiles given source code using Java compiler and returns API representation
   * extracted by ClassToAPI class.
   */
  private def extractApisFromSrc(src: (String, String)): Set[Companions] = {
    val (Seq(tempSrcFile), analysisCallback) =
      JavaCompilerForUnitTesting.compileJavaSrcs(src)(readAPI)
    val apis = analysisCallback.apis(tempSrcFile)
    apis.groupBy(_.name).map(companions.tupled).toSet
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

  private def findDeclaredInnerClass(
      classApi: ClassLike,
      innerClassName: String,
      defType: DefinitionType
  ): Option[ClassLikeDef] = {
    classApi.structure.declared.collectFirst({
      case c: ClassLikeDef if c.name == innerClassName && c.definitionType == defType => c
    })
  }

  def readAPI(
      callback: AnalysisCallback,
      source: VirtualFileRef,
      classes: Seq[Class[?]]
  ): Set[(String, String)] = {
    val (apis, mainClasses, inherits) = ClassToAPI.process(classes)
    apis.foreach(callback.api(source, _))
    mainClasses.foreach(callback.mainClass(source, _))
    inherits.map {
      case (from, to) => (from.getName, to.getName)
    }
  }

}
