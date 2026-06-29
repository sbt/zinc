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
import scala.util.Using
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

  // Regression: NPE in loadInnerClass when a JDK ancestor is bootstrap-loaded
  // and has a public InnerClasses entry (e.g. java.lang.Thread.Builder on JDK 21+).
  it should "not throw NPE when a parent JDK class is bootstrap-loaded" in {
    IO.withTemporaryDirectory { temp =>
      val outDir = new File(temp, "out")
      outDir.mkdir()
      val src = new File(temp, "MyThread.java")
      IO.write(src, "public class MyThread extends Thread {}")
      compileJava(Seq(src), outDir, Seq.empty)

      Using.resource(new java.net.URLClassLoader(Array(outDir.toURI.toURL))) { classloader =>
        val myThread = classloader.loadClass("MyThread")
        val (apis, _, _) = ClassToAPI.process(Seq(myThread))
        // Force the lazy structure so the inner-class walk runs.
        apis.foreach(_.structure.declared.toIndexedSeq)
        assert(apis.map(_.name).toSet.contains("MyThread"))
      }
    }
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

      Using.resource(new java.net.URLClassLoader(Array(srcDir.toURI.toURL), null)) { classloader =>
        val outerClass = classloader.loadClass("Outer")
        val (apis, _, _) = ClassToAPI.process(Seq(outerClass))

        val names = apis.map(_.name).toSet
        assert(names.contains("Outer"))
        assert(!names.contains("Outer.Inner"))
      }
    }
  }

  // sbt/zinc#837
  it should "not throw IllegalAccessError when inner class superclass is inaccessible" in {
    IO.withTemporaryDirectory { temp =>
      val libDir = new File(temp, "lib")
      val srcDir = new File(temp, "src")
      libDir.mkdir()
      srcDir.mkdir()

      // Compile against a public Base so Outer.Inner compiles cleanly.
      val baseFile = new File(temp, "Base.java")
      IO.write(baseFile, "package pkg; public class Base {}")
      compileJava(Seq(baseFile), libDir, Seq.empty)

      val outerFile = new File(temp, "Outer.java")
      IO.write(
        outerFile,
        """|public class Outer {
           |  public class Inner extends pkg.Base {}
           |  public void hello() {}
           |}
           |""".stripMargin
      )
      compileJava(Seq(outerFile), srcDir, Seq(libDir))

      // Overwrite Base with a package-private version: Outer$Inner (in the default package) can no
      // longer access its superclass, so loading it throws IllegalAccessError at link time, the
      // same failure mode as classes compiled with --add-exports.
      IO.write(baseFile, "package pkg; class Base {}")
      compileJava(Seq(baseFile), srcDir, Seq.empty)

      Using.resource(new java.net.URLClassLoader(Array(srcDir.toURI.toURL), null)) { classloader =>
        val outerClass = classloader.loadClass("Outer")
        val (apis, _, _) = ClassToAPI.process(Seq(outerClass))

        val names = apis.map(_.name).toSet
        assert(names.contains("Outer"))
        assert(!names.contains("Outer.Inner"))
      }
    }
  }

  // sbt/zinc#837 completeness probe: a class that LOADS but references an inaccessible type only in
  // its generic supertype and member positions must not crash ClassToAPI. Reflection resolves such
  // types without an access check, so IllegalAccessError only arises at class-load (caught by
  // JavaAnalyze.load / loadInnerClass), not during the reflective supertype/member walk.
  it should "not crash when a loaded class references an inaccessible type in supertype/members" in {
    IO.withTemporaryDirectory { temp =>
      val srcDir = new File(temp, "src")
      srcDir.mkdir()

      val containerFile = new File(temp, "Container.java")
      IO.write(containerFile, "package pkg; public class Container<T> {}")
      val secretFile = new File(temp, "Secret.java")
      IO.write(secretFile, "package pkg; public class Secret {}")
      val holderFile = new File(temp, "Holder.java")
      IO.write(
        holderFile,
        """|public class Holder extends pkg.Container<pkg.Secret> {
           |  public pkg.Secret field;
           |  public pkg.Secret get(pkg.Secret p) { return null; }
           |}
           |""".stripMargin
      )
      compileJava(Seq(containerFile, secretFile, holderFile), srcDir, Seq.empty)

      // Make Secret package-private: Holder (default package) can no longer access it, but its raw
      // supertype pkg.Container stays public so Holder still loads. Secret is now reachable only via
      // the generic supertype signature and Holder's members.
      IO.write(secretFile, "package pkg; class Secret {}")
      compileJava(Seq(secretFile), srcDir, Seq.empty)

      Using.resource(new java.net.URLClassLoader(Array(srcDir.toURI.toURL), null)) { classloader =>
        val holder = classloader.loadClass("Holder")
        val (apis, _, _) = ClassToAPI.process(Seq(holder))
        assert(apis.map(_.name).toSet.contains("Holder"))
      }
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
