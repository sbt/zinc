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
package classfile

import java.io.File
import sbt.internal.util.ConsoleLogger
import sbt.io.IO

class ParserSpecification extends UnitSpec {

  val sampleClasses = List[Class[?]](
    this.getClass,
    classOf[java.lang.Integer],
    classOf[java.util.AbstractMap.SimpleEntry[String, String]],
    classOf[String],
    classOf[Thread],
    classOf[org.scalacheck.Properties],
    // exercises meta-annotation parsing
    classOf[java.lang.annotation.Retention]
    // I thought it would be nice to throw in a nested annotation example here,
    // but I couldn't find one that we could use without having to add another
    // JAR to the test classpath. it's fine, we have nested annotation testing
    // over in AnalyzeSpecification
  )

  for (c <- sampleClasses)
    "classfile.Parser" should s"not crash when parsing $c" in {
      val logger = ConsoleLogger()
      // logger.setLevel(sbt.util.Level.Debug)
      val classfile = Parser(sbt.io.IO.classfileLocation(c), logger)
      assert(classfile ne null)
      assert(classfile.types.nonEmpty)
    }

  it should "parse InnerClasses attribute for AbstractMap.SimpleEntry" in {
    val logger = ConsoleLogger()
    val c = classOf[java.util.AbstractMap.SimpleEntry[String, String]]
    val cf = Parser(sbt.io.IO.classfileLocation(c), logger)
    val innerClasses = cf.innerClasses
    assert(innerClasses.nonEmpty)
    val self = innerClasses.find(_.innerClassName == "java.util.AbstractMap$SimpleEntry")
    assert(self.isDefined)
    assert(self.get.outerClassName == "java.util.AbstractMap")
  }

  it should "parse InnerClasses attribute for AbstractMap" in {
    val logger = ConsoleLogger()
    val c = classOf[java.util.AbstractMap[?, ?]]
    val cf = Parser(sbt.io.IO.classfileLocation(c), logger)
    val innerClasses = cf.innerClasses
    val entry = innerClasses.find(_.innerClassName == "java.util.AbstractMap$SimpleEntry")
    assert(entry.isDefined)
    assert(entry.get.outerClassName == "java.util.AbstractMap")
    assert(entry.get.isPublic)
  }

  it should "parse Exceptions attribute for Thread.sleep" in {
    val logger = ConsoleLogger()
    val cf = Parser(sbt.io.IO.classfileLocation(classOf[Thread]), logger)
    val sleep = cf.methods
      .find(m => m.name.contains("sleep") && m.descriptor.contains("(J)V"))
      .getOrElse(fail("Thread.sleep(long) not found"))
    val exceptions = cf.methodExceptions(sleep.attributes)
    assert(exceptions.contains("java.lang.InterruptedException"))
  }

  it should "expose access-flag predicates on FieldOrMethodInfo" in {
    val logger = ConsoleLogger()
    val stringCf = Parser(sbt.io.IO.classfileLocation(classOf[String]), logger)

    val ctor = stringCf.methods
      .find(m => m.isConstructor && m.descriptor.contains("(Ljava/lang/String;)V"))
      .getOrElse(fail("String(String) constructor not found"))
    assert(ctor.isConstructor)
    assert(!ctor.isStatic)
    assert(!ctor.isStaticInit)

    // String.format(String, Object...) is varargs
    val format = stringCf.methods
      .find(m =>
        m.name.contains("format") &&
          m.descriptor.exists(d =>
            d.startsWith("(Ljava/lang/String;[Ljava/lang/Object;)") && d.endsWith(
              "Ljava/lang/String;"
            )
          )
      )
      .getOrElse(fail("String.format(String, Object...) not found"))
    assert(format.isVarArgs)
    assert(format.isStatic)

    val boolCf = Parser(sbt.io.IO.classfileLocation(classOf[java.lang.Boolean]), logger)
    val trueField = boolCf.fields
      .find(_.name.contains("TRUE"))
      .getOrElse(fail("Boolean.TRUE field not found"))
    assert(trueField.isStatic)
    assert(trueField.isFinal)
    assert(trueField.isPublic)
  }

  it should "parse all element_value kinds in RuntimeVisibleAnnotations" in {
    IO.withTemporaryDirectory { temp =>
      val sources = Map(
        "TestEnum.java" -> "public enum TestEnum { A, B }",
        "Nested.java" ->
          """|import java.lang.annotation.*;
             |@Retention(RetentionPolicy.RUNTIME)
             |public @interface Nested { String value(); }
             |""".stripMargin,
        "TestAnn.java" ->
          """|import java.lang.annotation.*;
             |@Retention(RetentionPolicy.RUNTIME)
             |public @interface TestAnn {
             |  String s();
             |  int i();
             |  boolean b();
             |  char c();
             |  double d();
             |  float f();
             |  long j();
             |  byte bytev();
             |  short shortv();
             |  TestEnum e();
             |  Class<?> cls();
             |  int[] arr();
             |  Nested nested();
             |}
             |""".stripMargin,
        "Subject.java" ->
          """|public class Subject {
             |  @TestAnn(s="hello", i=42, b=true, c='x', d=3.14, f=2.5f, j=100L,
             |           bytev=1, shortv=2, e=TestEnum.A, cls=String.class,
             |           arr={1,2,3}, nested=@Nested(value="n"))
             |  public void m() {}
             |}
             |""".stripMargin,
      )
      val outDir = compileJavaSources(temp, sources)
      val cf = Parser(new File(outDir, "Subject.class"), ConsoleLogger())
      val m = cf.methods
        .find(_.name.contains("m"))
        .getOrElse(fail("method m() not found"))
      val ann = cf
        .annotations(m.attributes)
        .find(_.typeDescriptor == "LTestAnn;")
        .getOrElse(fail("@TestAnn not found on method m()"))
      val args = ann.arguments.map(a => a.name -> a.value).toMap
      assert(args("s") == "\"hello\"")
      assert(args("i") == "42")
      assert(args("b") == "true")
      assert(args("c") == "'x'")
      assert(args("d") == "3.14d")
      assert(args("f") == "2.5f")
      assert(args("j") == "100L")
      assert(args("bytev") == "(byte)1")
      assert(args("shortv") == "(short)2")
      assert(args("e") == "TestEnum.A")
      assert(args("cls") == "java.lang.String.class")
      assert(args("arr") == "{1, 2, 3}")
      assert(args("nested") == "@Nested(value=\"n\")")
    }
  }

  it should "parse parameter annotations by position" in {
    IO.withTemporaryDirectory { temp =>
      val sources = Map(
        "PA.java" ->
          """|import java.lang.annotation.*;
             |@Retention(RetentionPolicy.RUNTIME)
             |@Target(ElementType.PARAMETER)
             |public @interface PA {}
             |""".stripMargin,
        "PB.java" ->
          """|import java.lang.annotation.*;
             |@Retention(RetentionPolicy.RUNTIME)
             |@Target(ElementType.PARAMETER)
             |public @interface PB {}
             |""".stripMargin,
        "Subject.java" ->
          """|public class Subject {
             |  public void m(@PA String a, String b, @PA @PB String c) {}
             |}
             |""".stripMargin,
      )
      val outDir = compileJavaSources(temp, sources)
      val cf = Parser(new File(outDir, "Subject.class"), ConsoleLogger())
      val m = cf.methods
        .find(mm =>
          mm.name.contains("m") && mm.descriptor.exists(
            _ == "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
          )
        )
        .getOrElse(fail("m(String, String, String) not found"))
      val pas = cf.parameterAnnotations(m.attributes)
      assert(pas.length == 3, s"expected 3 parameter slots, got ${pas.length}")
      assert(pas(0).map(_.typeDescriptor).toSet == Set("LPA;"))
      assert(pas(1).isEmpty, s"expected no annotations on parameter 1, got ${pas(1)}")
      assert(pas(2).map(_.typeDescriptor).toSet == Set("LPA;", "LPB;"))
    }
  }

  it should "detect bridge methods on generic classes" in {
    IO.withTemporaryDirectory { temp =>
      val sources = Map(
        "Comp.java" ->
          """|import java.util.Comparator;
             |public class Comp implements Comparator<String> {
             |  @Override public int compare(String a, String b) { return a.compareTo(b); }
             |}
             |""".stripMargin
      )
      val outDir = compileJavaSources(temp, sources)
      val cf = Parser(new File(outDir, "Comp.class"), ConsoleLogger())
      // The bridge has the erased descriptor (Ljava/lang/Object;Ljava/lang/Object;)I.
      val bridge = cf.methods.find(m =>
        m.name.contains("compare") &&
          m.descriptor.contains("(Ljava/lang/Object;Ljava/lang/Object;)I")
      )
      assert(bridge.isDefined, "expected an erasure bridge for compare")
      assert(bridge.get.isBridge)
      assert(bridge.get.isSynthetic)
    }
  }

  private def compileJavaSources(tempDir: File, sources: Map[String, String]): File = {
    import javax.tools.{ StandardLocation, ToolProvider }
    import scala.jdk.CollectionConverters._
    val srcDir = new File(tempDir, "src")
    val outDir = new File(tempDir, "out")
    srcDir.mkdir()
    outDir.mkdir()
    val files = sources.toSeq.map { case (name, src) =>
      val f = new File(srcDir, name); IO.write(f, src); f
    }
    val compiler = ToolProvider.getSystemJavaCompiler()
    val fileManager = compiler.getStandardFileManager(null, null, null)
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Seq(outDir).asJava)
    val units = fileManager.getJavaFileObjectsFromFiles(files.asJava)
    val ok = compiler.getTask(null, fileManager, null, null, null, units).call()
    fileManager.close()
    assert(ok, s"javac failed compiling fixtures in $srcDir")
    outDir
  }

}
