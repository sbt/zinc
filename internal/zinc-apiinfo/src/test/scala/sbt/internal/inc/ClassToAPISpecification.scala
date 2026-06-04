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

  // sbt/sbt#117 (reflection-fallback variant): a method's return type lives in
  // an optional dep that's no longer on the classpath. Reflection throws when
  // forced; the classfile-based fallback should rebuild the Def from the descriptor.
  it should "fall back to classfile parser when a method return type is missing" in {
    withMissingDep("public Missing foo() { return null; }") { (api, name) =>
      val outer = api.find(_.name == "Outer").get
      val foo = outer.structure.declared.collectFirst {
        case d: xsbti.api.Def if d.name == "foo" => d
      }.getOrElse(fail(s"foo() not in declared: ${outer.structure.declared.toSeq}"))
      assert(projectionId(foo.returnType).contains("Missing"))
    }
  }

  it should "fall back to classfile parser when a method parameter type is missing" in {
    withMissingDep("public void foo(Missing m) {}") { (api, _) =>
      val outer = api.find(_.name == "Outer").get
      val foo = outer.structure.declared.collectFirst {
        case d: xsbti.api.Def if d.name == "foo" => d
      }.getOrElse(fail("foo(Missing) not in declared"))
      val paramType = foo.valueParameters.head.parameters.head.tpe
      assert(projectionId(paramType).contains("Missing"))
    }
  }

  it should "fall back to classfile parser when a field type is missing" in {
    withMissingDep("public Missing field;") { (api, _) =>
      val outer = api.find(_.name == "Outer").get
      val field = outer.structure.declared.collectFirst {
        case v: xsbti.api.Var if v.name == "field" => v
      }.getOrElse(fail("field not in declared"))
      assert(projectionId(field.tpe).contains("Missing"))
    }
  }

  // Known limitation: when only the annotation *type* is missing (and nothing
  // else in the class triggers a reflection failure), modern JDKs silently
  // drop the unresolvable Annotation from c.getAnnotations() rather than
  // throwing. classAnnotationsSafe only triggers fallback on exception, so
  // the @Missing annotation is lost. Recovering it would require either
  // always reading class annotations from the classfile (Phase 4 territory)
  // or detecting count mismatches between reflection and classfile.
  it should "(limitation) lose missing-type class annotations when reflection silently drops them" in {
    IO.withTemporaryDirectory { temp =>
      val libDir = new File(temp, "lib"); libDir.mkdir()
      val srcDir = new File(temp, "src"); srcDir.mkdir()

      val missingAnn = new File(temp, "Missing.java")
      IO.write(
        missingAnn,
        """|import java.lang.annotation.*;
           |@Retention(RetentionPolicy.RUNTIME)
           |public @interface Missing {}
           |""".stripMargin
      )
      compileJava(Seq(missingAnn), libDir, Seq.empty)

      val outerFile = new File(temp, "Outer.java")
      IO.write(outerFile, "@Missing public class Outer {}")
      compileJava(Seq(outerFile), srcDir, Seq(libDir))

      Using.resource(new java.net.URLClassLoader(Array(srcDir.toURI.toURL), null)) { cl =>
        val outerClass = cl.loadClass("Outer")
        val (apis, _, _) = ClassToAPI.process(Seq(outerClass))
        val outer = apis.find(_.name == "Outer").get
        // Pins current behavior. When we fix the limitation, this assertion
        // will start failing and should flip to .exists(... "Missing" ...).
        assert(!outer.annotations.exists(a => projectionId(a.base).contains("Missing")))
      }
    }
  }

  // Regression: inherited members carry attributes whose constant-pool indices
  // belong to the *parent's* classfile, not the child's. The classfile fallback
  // must read those attributes with the parent's ClassFile or it returns garbage
  // (or trips the `entry.tag == ConstantUTF8` assertion in Parser#toUTF8).
  it should "read inherited member annotations against the parent's constant pool" in {
    IO.withTemporaryDirectory { temp =>
      val libDir = new File(temp, "lib"); libDir.mkdir()
      val srcDir = new File(temp, "src"); srcDir.mkdir()

      val missing = new File(temp, "Missing.java")
      IO.write(missing, "public class Missing {}")
      compileJava(Seq(missing), libDir, Seq.empty)

      val parent = new File(temp, "Parent.java")
      IO.write(
        parent,
        """|public class Parent {
           |  // Reference to Missing forces the reflection path to throw,
           |  // which triggers the classfile fallback on Child too.
           |  public Missing dep() { return null; }
           |  @Deprecated public void inheritedMethod() {}
           |}
           |""".stripMargin
      )
      val child = new File(temp, "Child.java")
      IO.write(child, "public class Child extends Parent {}")
      compileJava(Seq(parent, child), srcDir, Seq(libDir))

      Using.resource(new java.net.URLClassLoader(Array(srcDir.toURI.toURL), null)) { cl =>
        val childClass = cl.loadClass("Child")
        val (apis, _, _) = ClassToAPI.process(Seq(childClass))
        val childApi = apis.find(_.name == "Child").get
        val inheritedMethod = childApi.structure.inherited.collectFirst {
          case d: xsbti.api.Def if d.name == "inheritedMethod" => d
        }.getOrElse {
          fail(
            s"inheritedMethod not found in Child.structure.inherited: " +
              childApi.structure.inherited.map(_.name).mkString(", ")
          )
        }
        assert(
          inheritedMethod.annotations.exists(a => projectionId(a.base).contains("Deprecated")),
          s"expected @Deprecated on inherited method; got: " +
            inheritedMethod.annotations.map(a => projectionId(a.base)).mkString(", ")
        )
      }
    }
  }

  it should "emit @throws annotations on classfile-fallback methods" in {
    withMissingDep(
      """|public Missing dep() { return null; }
         |public void thrower() throws java.io.IOException {}
         |""".stripMargin
    ) { (api, _) =>
      val outer = api.find(_.name == "Outer").get
      val thrower = outer.structure.declared.collectFirst {
        case d: xsbti.api.Def if d.name == "thrower" => d
      }.getOrElse(fail("thrower() not in declared"))
      val throwsAnn = thrower.annotations.find(a => projectionId(a.base).contains("throws"))
      assert(throwsAnn.isDefined, s"no @throws annotation: ${thrower.annotations.toSeq}")
      assert(
        throwsAnn.get.arguments.exists(_.value.contains("IOException")),
        s"expected IOException in throws args; got: ${throwsAnn.get.arguments.toSeq}"
      )
    }
  }

  it should "log a warning when falling back to the classfile parser" in {
    IO.withTemporaryDirectory { temp =>
      val libDir = new File(temp, "lib"); libDir.mkdir()
      val srcDir = new File(temp, "src"); srcDir.mkdir()
      val missing = new File(temp, "Missing.java")
      IO.write(missing, "public class Missing {}")
      compileJava(Seq(missing), libDir, Seq.empty)
      val outer = new File(temp, "Outer.java")
      IO.write(outer, "public class Outer { public Missing foo() { return null; } }")
      compileJava(Seq(outer), srcDir, Seq(libDir))

      Using.resource(new java.net.URLClassLoader(Array(srcDir.toURI.toURL), null)) { cl =>
        val outerClass = cl.loadClass("Outer")
        val recording = new RecordingLogger
        val (_, _, _) = ClassToAPI.process(Seq(outerClass), recording)
        assert(
          recording.warns.exists(_.contains("falling back to classfile parser")),
          s"no fallback-warn observed; messages: ${recording.warns.mkString(" | ")}"
        )
      }
    }
  }

  // ---- helpers ----

  private def withMissingDep(outerBody: String)(
      check: (Seq[ClassLike], String) => Unit
  ): Unit = IO.withTemporaryDirectory { temp =>
    val libDir = new File(temp, "lib"); libDir.mkdir()
    val srcDir = new File(temp, "src"); srcDir.mkdir()

    val missing = new File(temp, "Missing.java")
    IO.write(missing, "public class Missing {}")
    compileJava(Seq(missing), libDir, Seq.empty)

    val outerFile = new File(temp, "Outer.java")
    IO.write(outerFile, s"public class Outer {\n$outerBody\n}\n")
    compileJava(Seq(outerFile), srcDir, Seq(libDir))

    Using.resource(new java.net.URLClassLoader(Array(srcDir.toURI.toURL), null)) { cl =>
      val outerClass = cl.loadClass("Outer")
      val (apis, _, _) = ClassToAPI.process(Seq(outerClass))
      check(apis, "Outer")
    }
  }

  private def projectionId(t: xsbti.api.Type): Option[String] = t match {
    case p: xsbti.api.Projection => Some(p.id)
    case _                       => None
  }

  private class RecordingLogger extends sbt.util.Logger {
    private val buf = collection.mutable.ListBuffer.empty[(sbt.util.Level.Value, String)]
    override def trace(t: => Throwable): Unit = ()
    override def success(message: => String): Unit = ()
    override def log(level: sbt.util.Level.Value, message: => String): Unit =
      buf.synchronized(buf += ((level, message)))
    def warns: Seq[String] =
      buf.synchronized(buf.collect { case (sbt.util.Level.Warn, msg) => msg }.toSeq)
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
