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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.io.File
import sbt.internal.inc.classfile.{ JavaCompilerForUnitTesting, Parser }
import sbt.io.IO
import sbt.util.Logger
import xsbt.api.HashAPI
import xsbti.api.{ ClassLike, DefinitionType }

object ClassfileToAPISpecification extends Properties:
  override def tests: List[Test] = List(
    example(
      "ClassfileToAPI should build a deterministic API whose hash tracks the class's public shape",
      deterministicHash
    ),
    example("record API for an un-loadable inner class through JavaAnalyze", unloadableInnerClass),
    example(
      "split static/instance members and parse array and multi-arg descriptors",
      staticAndInstance
    ),
    example(
      "degrade gracefully on malformed or truncated descriptors instead of throwing",
      malformedDescriptors
    ),
    property("never throw on any field or method descriptor", propDescriptors),
    property("parse every parameter of a well-formed method descriptor", propMethodArity),
    example("detect generic Signature changes that share an erased descriptor", genericSignature),
    example("detect static final constant value changes", constantValue),
    example("detect checked-exception (throws) changes", checkedException),
    example("detect added or removed declared annotations", declaredAnnotations),
    property("hash method signatures equally exactly when they are equal", propSignatureHash)
      .withTests(10),
    example("treat only a method named main as a main class", onlyMain),
  )

  private def compileSample(temp: File, dirName: String, source: String): ClassLike =
    sampleApis(temp, dirName, source).find(_.definitionType == DefinitionType.ClassDef).get

  // The point of Phase 2 (sbt/zinc#837): a class that can't be reflectively loaded still gets an
  // API recorded from its classfile, and that API's hash changes when the class's public shape
  // changes — so name-hashing invalidates dependents. The hash need not match the reflection-derived
  // one; it only needs to be deterministic and shape-sensitive.
  private def deterministicHash: Result = IO.withTemporaryDirectory { temp =>
    val intGreet = "public class Sample { public int greet(int n) { return 0; } }"
    val a = compileSample(temp, "a", intGreet)
    val aAgain = compileSample(temp, "a2", intGreet)
    // changed return type -> different public shape
    val b = compileSample(
      temp,
      "b",
      "public class Sample { public String greet(int n) { return null; } }"
    )
    // sanity: the method was actually modelled
    val defs = a.structure.declared.collect { case d: xsbti.api.Def => d.name }.toSet
    Result.all(
      List(
        (HashAPI(a) ==== HashAPI(aAgain)).log("hash must be deterministic across runs"),
        Result
          .diff(HashAPI(a), HashAPI(b))(_ != _)
          .log("hash must change when a method's signature changes"),
        Result.assert(defs.contains("greet")).log(s"defs: $defs"),
      )
    )
  }

  // Full path: the #837 un-loadable inner class now gets an API recorded via JavaAnalyze ->
  // readClassfileAPI -> ClassfileToAPI, including its declared members.
  private def unloadableInnerClass: Result = IO.withTemporaryDirectory { temp =>
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
      (cb, src, named) =>
        val (apis, _) = ClassfileToAPI.process(named)
        apis.foreach(cb.api(src, _))
    )
    val recorded = callback.apis.values.flatten.toSet
    recorded.find(c => c.name == "Outer.Inner" && c.definitionType == DefinitionType.ClassDef) match
      case Some(innerClass) =>
        val memberDefs =
          innerClass.structure.declared.collect { case d: xsbti.api.Def => d.name }.toSet
        Result.assert(memberDefs.contains("answer")).log(s"members: $memberDefs")
      case None => Result.failure.log(s"recorded: ${recorded.map(_.name)}")
  }

  private def staticAndInstance: Result = IO.withTemporaryDirectory { temp =>
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
    Result.all(
      List(
        Result.diff(names(cls), Set("inst", "arr"))((a, b) => b.subsetOf(a)),
        Result.diff(names(mod), Set("stat", "run"))((a, b) => b.subsetOf(a)),
        Result.diff(names(cls), Set("stat", "run"))((a, b) => (a & b).isEmpty),
        Result.diff(names(mod), Set("inst", "arr"))((a, b) => (a & b).isEmpty),
      )
    )
  }

  // The fallback runs only for classes that already failed to load, so it must never throw on an
  // odd/truncated descriptor and turn a gracefully-skipped class into a compile failure.
  private def malformedDescriptors: Result =
    Result.all(
      List(
        // None of these should throw; each yields some (best-effort) type.
        Result.assert(ClassfileToAPI.parseFieldType("Ljava/lang/String") ne null), // missing ';'
        Result.assert(ClassfileToAPI.parseFieldType("Q") ne null), // unknown token
        Result.assert(ClassfileToAPI.parseFieldType("") ne null), // empty
        Result.assert(ClassfileToAPI.parseMethodType("(I")._2 ne null), // missing ')'
        Result.assert(ClassfileToAPI.parseMethodType("()")._2 ne null), // missing return type
        Result.assert(ClassfileToAPI.parseMethodType("")._2 ne null), // empty

        // Well-formed descriptors still parse to the right arity.
        ClassfileToAPI.parseMethodType("(Ljava/lang/String;[I)V")._1.length ==== 2,
      )
    )

  private val genDescriptorChar: Gen[Char] =
    Gen.frequency1(
      8 -> Gen.element1('B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 'V', 'L', ';', '[', '(', ')', '/'),
      2 -> Gen.alphaNum,
      1 -> Gen.unicode,
    )

  def propDescriptors: Property =
    for descriptor <- Gen.string(genDescriptorChar, Range.linear(0, 32)).forAll
    yield Result
      .all(
        List(
          Result.assert(ClassfileToAPI.parseFieldType(descriptor) ne null),
          Result.assert(ClassfileToAPI.parseMethodType(descriptor)._2 ne null),
        )
      )
      .log(s"descriptor: $descriptor")

  private val genFieldDescriptor: Gen[String] =
    for
      dims <- Gen.int(Range.linear(0, 3))
      base <- Gen.choice1(
        Gen.element1("B", "C", "D", "F", "I", "J", "S", "Z"),
        Gen
          .list(Gen.string(Gen.alpha, Range.linear(1, 6)), Range.linear(1, 3))
          .map(_.mkString("L", "/", ";")),
      )
    yield "[" * dims + base

  def propMethodArity: Property =
    for
      params <- Gen.list(genFieldDescriptor, Range.linear(0, 6)).forAll
      result <- Gen.choice1(Gen.constant("V"), genFieldDescriptor).forAll
    yield
      val descriptor = params.mkString("(", "", ")") + result
      (ClassfileToAPI.parseMethodType(descriptor)._1.length ==== params.size)
        .log(s"descriptor: $descriptor")

  // P2a: generic-only changes share an erased descriptor, so without folding the raw Signature
  // attribute into the API this would be missed.
  private def genericSignature: Result = IO.withTemporaryDirectory { temp =>
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
    Result.diff(hashAll(a), hashAll(b))(_ != _)
  }

  // P2b: compilers inline static-final constants, so a value change must change the hash even though
  // the field descriptor is unchanged.
  private def constantValue: Result = IO.withTemporaryDirectory { temp =>
    val a = sampleApis(temp, "a", "public class Sample { public static final int X = 1; }")
    val b = sampleApis(temp, "b", "public class Sample { public static final int X = 2; }")
    Result.diff(hashAll(a), hashAll(b))(_ != _)
  }

  // P2 (exceptions): adding a checked exception changes the Java contract (callers must handle it),
  // even though the descriptor is unchanged, so the hash must change.
  private def checkedException: Result = IO.withTemporaryDirectory { temp =>
    val a = sampleApis(temp, "a", "public class Sample { public void run() {} }")
    val b = sampleApis(
      temp,
      "b",
      "import java.io.IOException;\npublic class Sample { public void run() throws IOException {} }"
    )
    Result.diff(hashAll(a), hashAll(b))(_ != _)
  }

  // P3 (annotations): a declared annotation is public shape for annotation-driven clients.
  private def declaredAnnotations: Result = IO.withTemporaryDirectory { temp =>
    val a = sampleApis(temp, "a", "public class Sample { public void run() {} }")
    val b = sampleApis(temp, "b", "public class Sample { @Deprecated public void run() {} }")
    Result.diff(hashAll(a), hashAll(b))(_ != _)
  }

  private val genJavaType: Gen[String] =
    Gen.element1("int", "long", "String", "int[]", "java.util.List<String>", "java.util.List<Long>")

  private val genSignature: Gen[(String, List[String], Boolean)] =
    for
      result <- Gen.choice1(Gen.constant("void"), genJavaType)
      params <- Gen.list(genJavaType, Range.linear(0, 3))
      throws <- Gen.boolean
    yield (result, params, throws)

  private def render(signature: (String, List[String], Boolean)): String =
    val (result, params, throws) = signature
    val body = if result == "void" then "" else s"return ${defaultValue(result)};"
    val throwsClause = if throws then " throws java.io.IOException" else ""
    val paramList = params.zipWithIndex.map((t, i) => s"$t p$i").mkString(", ")
    s"public class Sample { public $result run($paramList)$throwsClause { $body } }"

  private def defaultValue(tpe: String): String = tpe match
    case "int" | "long" => "0"
    case _              => "null"

  def propSignatureHash: Property =
    for
      left <- genSignature.forAll
      right <- Gen.choice1(Gen.constant(left), genSignature).forAll
    yield IO.withTemporaryDirectory { temp =>
      val a = hashAll(sampleApis(temp, "a", render(left)))
      val b = hashAll(sampleApis(temp, "b", render(right)))
      (if left == right then a ==== b else Result.diff(a, b)(_ != _))
        .log(s"${render(left)}\n${render(right)}")
    }

  // P3: only a method actually named `main` (not just any public-static-void(String[])) is a main.
  private def onlyMain: Result = IO.withTemporaryDirectory { temp =>
    def mainsOf(dirName: String, source: String): Seq[String] =
      val dir = new File(temp, dirName)
      dir.mkdir()
      val src = new File(dir, "Sample.java")
      IO.write(src, source)
      JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
      val cf = Parser(new File(dir, "Sample.class").toPath, Logger.Null)
      ClassfileToAPI.process(Seq("Sample" -> cf))._2
    Result.all(
      List(
        // same descriptor as main but a different name -> not a main class
        mainsOf("a", "public class Sample { public static void foo(String[] a) {} }") ==== Nil,
        mainsOf("b", "public class Sample { public static void main(String[] a) {} }") ==== Seq(
          "Sample"
        ),
      )
    )
  }

  private def sampleApis(temp: File, dirName: String, source: String): Seq[ClassLike] =
    val dir = new File(temp, dirName)
    dir.mkdir()
    val src = new File(dir, "Sample.java")
    IO.write(src, source)
    JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
    val cf = Parser(new File(dir, "Sample.class").toPath, Logger.Null)
    ClassfileToAPI.process(Seq("Sample" -> cf))._1

  private def hashAll(apis: Seq[ClassLike]): Int = apis.map(HashAPI(_)).sum
end ClassfileToAPISpecification
