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
import sbt.internal.inc.classfile.{ JavaCompilerForUnitTesting, Parser }
import sbt.io.IO
import sbt.util.Logger
import xsbt.api.HashAPI
import xsbti.api.{ ClassLike, DefinitionType }

class ClassfileToAPISpecification extends UnitSpec {

  // The point of Phase 2 (sbt/zinc#837): a class that can't be reflectively loaded still gets an
  // API recorded from its classfile, and that API's hash changes when the class's public shape
  // changes — so name-hashing invalidates dependents. The hash need not match the reflection-derived
  // one; it only needs to be deterministic and shape-sensitive.
  "ClassfileToAPI" should "build a deterministic API whose hash tracks the class's public shape" in {
    IO.withTemporaryDirectory { temp =>
      def classApi(dirName: String, source: String): ClassLike = {
        val dir = new File(temp, dirName)
        dir.mkdir()
        val src = new File(dir, "Sample.java")
        IO.write(src, source)
        JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
        val cf = Parser(new File(dir, "Sample.class").toPath, Logger.Null)
        val (apis, _) = ClassfileToAPI.process(Seq("Sample" -> cf))
        apis.find(_.definitionType == DefinitionType.ClassDef).get
      }

      val intGreet = "public class Sample { public int greet(int n) { return 0; } }"
      val a = classApi("a", intGreet)
      val aAgain = classApi("a2", intGreet)
      // changed return type -> different public shape
      val b = classApi("b", "public class Sample { public String greet(int n) { return null; } }")

      assert(HashAPI(a) == HashAPI(aAgain), "hash must be deterministic across runs")
      assert(HashAPI(a) != HashAPI(b), "hash must change when a method's signature changes")

      // sanity: the method was actually modelled
      val defs = a.structure.declared.collect { case d: xsbti.api.Def => d.name }.toSet
      assert(defs.contains("greet"))
    }
  }

  // Full path: the #837 un-loadable inner class now gets an API recorded via JavaAnalyze ->
  // readClassfileAPI -> ClassfileToAPI, including its declared members.
  it should "record API for an un-loadable inner class through JavaAnalyze" in {
    IO.withTemporaryDirectory { temp =>
      val classesDir = new File(temp, "classes")
      val libDir = new File(temp, "lib")
      classesDir.mkdir()
      libDir.mkdir()

      val baseFile = new File(temp, "Base.java")
      IO.write(baseFile, "package pkg; public class Base {}")
      JavaCompilerForUnitTesting.compileJava(Seq(baseFile), libDir, Seq.empty)

      val outerFile = new File(temp, "Outer.java")
      IO.write(
        outerFile,
        """|public class Outer {
           |  public class Inner extends pkg.Base { public int answer() { return 42; } }
           |}
           |""".stripMargin
      )
      JavaCompilerForUnitTesting.compileJava(Seq(outerFile), classesDir, Seq(libDir))

      // Make Base package-private: Outer$Inner can no longer be loaded during analysis.
      IO.write(baseFile, "package pkg; class Base {}")
      JavaCompilerForUnitTesting.compileJava(Seq(baseFile), classesDir, Seq.empty)

      val callback = JavaCompilerForUnitTesting.analyze(
        classesDir,
        Seq(outerFile),
        (cb, src, named) => {
          val (apis, _) = ClassfileToAPI.process(named)
          apis.foreach(cb.api(src, _))
        }
      )

      val recorded = callback.apis.values.flatten.toSet
      assert(recorded.map(_.name).contains("Outer.Inner"))

      val innerClass = recorded
        .find(c => c.name == "Outer.Inner" && c.definitionType == DefinitionType.ClassDef)
        .get
      val memberDefs =
        innerClass.structure.declared.collect { case d: xsbti.api.Def => d.name }.toSet
      assert(memberDefs.contains("answer"))
    }
  }

  it should "split static/instance members and parse array and multi-arg descriptors" in {
    IO.withTemporaryDirectory { temp =>
      val src = new File(temp, "S.java")
      IO.write(
        src,
        """|public class S {
           |  public int inst;
           |  public static int stat;
           |  public int[] arr(String s, long[] xs) { return null; }
           |  public static void run() {}
           |}
           |""".stripMargin
      )
      JavaCompilerForUnitTesting.compileJava(Seq(src), temp, Seq.empty)
      val cf = Parser(new File(temp, "S.class").toPath, Logger.Null)
      val (apis, _) = ClassfileToAPI.process(Seq("S" -> cf))
      val cls = apis.find(_.definitionType == DefinitionType.ClassDef).get
      val mod = apis.find(_.definitionType == DefinitionType.Module).get
      def names(c: ClassLike): Set[String] = c.structure.declared.map(_.name).toSet

      // instance members on the class, static members on the module — and no leakage either way
      assert(names(cls).contains("inst") && names(cls).contains("arr"))
      assert(names(mod).contains("stat") && names(mod).contains("run"))
      assert(!names(cls).contains("stat") && !names(cls).contains("run"))
      assert(!names(mod).contains("inst") && !names(mod).contains("arr"))
    }
  }

  // The fallback runs only for classes that already failed to load, so it must never throw on an
  // odd/truncated descriptor and turn a gracefully-skipped class into a compile failure.
  it should "degrade gracefully on malformed or truncated descriptors instead of throwing" in {
    // None of these should throw; each yields some (best-effort) type.
    assert(ClassfileToAPI.parseFieldType("Ljava/lang/String") ne null) // missing ';'
    assert(ClassfileToAPI.parseFieldType("Q") ne null) // unknown token
    assert(ClassfileToAPI.parseFieldType("") ne null) // empty
    assert(ClassfileToAPI.parseMethodType("(I")._2 ne null) // missing ')'
    assert(ClassfileToAPI.parseMethodType("()")._2 ne null) // missing return type
    assert(ClassfileToAPI.parseMethodType("")._2 ne null) // empty

    // Well-formed descriptors still parse to the right arity.
    val (params, _) = ClassfileToAPI.parseMethodType("(Ljava/lang/String;[I)V")
    assert(params.length == 2)
  }

  // P2a: generic-only changes share an erased descriptor, so without folding the raw Signature
  // attribute into the API this would be missed.
  it should "detect generic Signature changes that share an erased descriptor" in {
    IO.withTemporaryDirectory { temp =>
      val a = sampleApis(
        temp,
        "a",
        "import java.util.List;\npublic class Sample { public List<String> answer() { return null; } }"
      )
      val b = sampleApis(
        temp,
        "b",
        "import java.util.List;\npublic class Sample { public List<Integer> answer() { return null; } }"
      )
      assert(hashAll(a) != hashAll(b))
    }
  }

  // P2b: compilers inline static-final constants, so a value change must change the hash even though
  // the field descriptor is unchanged.
  it should "detect static final constant value changes" in {
    IO.withTemporaryDirectory { temp =>
      val a = sampleApis(temp, "a", "public class Sample { public static final int X = 1; }")
      val b = sampleApis(temp, "b", "public class Sample { public static final int X = 2; }")
      assert(hashAll(a) != hashAll(b))
    }
  }

  // P2 (exceptions): adding a checked exception changes the Java contract (callers must handle it),
  // even though the descriptor is unchanged, so the hash must change.
  it should "detect checked-exception (throws) changes" in {
    IO.withTemporaryDirectory { temp =>
      val a = sampleApis(temp, "a", "public class Sample { public void run() {} }")
      val b = sampleApis(
        temp,
        "b",
        "import java.io.IOException;\npublic class Sample { public void run() throws IOException {} }"
      )
      assert(hashAll(a) != hashAll(b))
    }
  }

  // P3 (annotations): a declared annotation is public shape for annotation-driven clients.
  it should "detect added or removed declared annotations" in {
    IO.withTemporaryDirectory { temp =>
      val a = sampleApis(temp, "a", "public class Sample { public void run() {} }")
      val b = sampleApis(temp, "b", "public class Sample { @Deprecated public void run() {} }")
      assert(hashAll(a) != hashAll(b))
    }
  }

  // P3: only a method actually named `main` (not just any public-static-void(String[])) is a main.
  it should "treat only a method named main as a main class" in {
    IO.withTemporaryDirectory { temp =>
      def mainsOf(dirName: String, source: String): Seq[String] = {
        val dir = new File(temp, dirName)
        dir.mkdir()
        val src = new File(dir, "Sample.java")
        IO.write(src, source)
        JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
        val cf = Parser(new File(dir, "Sample.class").toPath, Logger.Null)
        ClassfileToAPI.process(Seq("Sample" -> cf))._2
      }
      // same descriptor as main but a different name -> not a main class
      assert(mainsOf("a", "public class Sample { public static void foo(String[] a) {} }").isEmpty)
      assert(mainsOf("b", "public class Sample { public static void main(String[] a) {} }") == Seq(
        "Sample"
      ))
    }
  }

  // Step 2: generic types are recovered from the Signature attribute as real api types, not folded.
  it should "model generic types and type parameters from the Signature attribute" in {
    IO.withTemporaryDirectory { temp =>
      val src = new File(temp, "Box.java")
      IO.write(
        src,
        """|import java.util.List;
           |public class Box<T> {
           |  public List<String> items;
           |  public <U> U get(U u) { return u; }
           |}
           |""".stripMargin
      )
      JavaCompilerForUnitTesting.compileJava(Seq(src), temp, Seq.empty)
      val cf = Parser(new File(temp, "Box.class").toPath, Logger.Null)
      val cls = ClassfileToAPI
        .process(Seq("Box" -> cf))
        ._1
        .find(_.definitionType == DefinitionType.ClassDef)
        .get

      // class type parameter T
      assert(cls.typeParameters.map(_.id).toSeq == Seq("T"))

      // field List<String> is a real Parameterized type, not erased or folded
      val items = cls.structure.declared.collectFirst {
        case f: xsbti.api.FieldLike if f.name == "items" => f
      }.get
      assert(items.tpe.isInstanceOf[xsbti.api.Parameterized])

      // method <U> U get(U) has type parameter U and returns ParameterRef U
      val get = cls.structure.declared.collectFirst {
        case d: xsbti.api.Def if d.name == "get" => d
      }.get
      assert(get.typeParameters.map(_.id).toSeq == Seq("U"))
      assert(get.returnType.asInstanceOf[xsbti.api.ParameterRef].id == "U")
    }
  }

  // Step 2b: annotation element values are captured, so a value change is detected.
  it should "detect annotation element value changes" in {
    IO.withTemporaryDirectory { temp =>
      val a = sampleApis(
        temp,
        "a",
        "public class Sample { @Deprecated(since = \"1\") public void run() {} }"
      )
      val b = sampleApis(
        temp,
        "b",
        "public class Sample { @Deprecated(since = \"2\") public void run() {} }"
      )
      assert(hashAll(a) != hashAll(b))
    }
  }

  // Step 2c: a Java enum gets sealed-children modelled (mirroring ClassToAPI); a class does not.
  it should "model enum children for an enum but not a plain class" in {
    IO.withTemporaryDirectory { temp =>
      def classDef(dirName: String, name: String, source: String): ClassLike = {
        val dir = new File(temp, dirName)
        dir.mkdir()
        val src = new File(dir, name + ".java")
        IO.write(src, source)
        JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
        val cf = Parser(new File(dir, name + ".class").toPath, Logger.Null)
        ClassfileToAPI
          .process(Seq(name -> cf))
          ._1
          .find(_.definitionType == DefinitionType.ClassDef)
          .get
      }
      assert(classDef("e", "E", "public enum E { A, B }").childrenOfSealedClass.nonEmpty)
      assert(classDef("c", "C", "public class C {}").childrenOfSealedClass.isEmpty)
    }
  }

  // Step 2d: differential check vs the reflection path (ClassToAPI) on declared member names.
  it should "agree with ClassToAPI on declared member names" in {
    IO.withTemporaryDirectory { temp =>
      val src = new File(temp, "Simple.java")
      IO.write(
        src,
        "public class Simple { public int x; public String greet(int n) { return null; } }"
      )
      JavaCompilerForUnitTesting.compileJava(Seq(src), temp, Seq.empty)
      val loader = new java.net.URLClassLoader(Array(temp.toURI.toURL), null)
      val reflectApi = ClassToAPI
        .process(Seq(loader.loadClass("Simple")))
        ._1
        .find(_.definitionType == DefinitionType.ClassDef)
        .get
      val cf = Parser(new File(temp, "Simple.class").toPath, Logger.Null)
      val classfileApi = ClassfileToAPI
        .process(Seq("Simple" -> cf))
        ._1
        .find(_.definitionType == DefinitionType.ClassDef)
        .get
      def names(c: ClassLike): Set[String] = c.structure.declared.map(_.name).toSet
      assert(
        names(classfileApi) == names(reflectApi),
        s"classfile=${names(classfileApi)} vs reflect=${names(reflectApi)}"
      )
    }
  }

  // P2: varargs (`T...`) and array (`T[]`) parameters share a descriptor but differ via ACC_VARARGS.
  it should "distinguish varargs from array parameters" in {
    IO.withTemporaryDirectory { temp =>
      val a = sampleApis(temp, "a", "public class Sample { public void m(String[] xs) {} }")
      val b = sampleApis(temp, "b", "public class Sample { public void m(String... xs) {} }")
      assert(hashAll(a) != hashAll(b))
    }
  }

  // P2: constructors get an Empty return type (as ClassToAPI does), not void/Unit.
  it should "give constructors an Empty return type" in {
    IO.withTemporaryDirectory { temp =>
      val src = new File(temp, "Sample.java")
      IO.write(src, "public class Sample { public Sample(int x) {} }")
      JavaCompilerForUnitTesting.compileJava(Seq(src), temp, Seq.empty)
      val cf = Parser(new File(temp, "Sample.class").toPath, Logger.Null)
      val cls = ClassfileToAPI
        .process(Seq("Sample" -> cf))
        ._1
        .find(_.definitionType == DefinitionType.ClassDef)
        .get
      val ctor = cls.structure.declared.collectFirst {
        case d: xsbti.api.Def if d.name.endsWith(";init;") => d
      }.get
      assert(ctor.returnType.isInstanceOf[xsbti.api.EmptyType])
    }
  }

  private def sampleApis(temp: File, dirName: String, source: String): Seq[ClassLike] = {
    val dir = new File(temp, dirName)
    dir.mkdir()
    val src = new File(dir, "Sample.java")
    IO.write(src, source)
    JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
    val cf = Parser(new File(dir, "Sample.class").toPath, Logger.Null)
    ClassfileToAPI.process(Seq("Sample" -> cf))._1
  }

  private def hashAll(apis: Seq[ClassLike]): Int = apis.map(HashAPI(_)).sum

}
