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
import java.net.URLClassLoader
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import sbt.internal.inc.classfile.{ ClassFile, JavaCompilerForUnitTesting, Parser }
import sbt.io.IO
import sbt.util.Logger
import xsbt.api.HashAPI
import xsbti.api.ClassLike

/**
 * Regression guard for the classfile-based Java API path against pathological generic signatures:
 * a stress corpus of deep and mutually recursive F-bounds, `L0 <-> Mirror` signature cycles,
 * covariant bridges, nested generic inners, and enum anonymous classes. The reflected `Type`
 * graph for these is genuinely cyclic, so any extractor that expands a type variable into its bound,
 * or chases method-return signatures, loops forever unless it stops at the variable. [[ClassfileToAPI]]
 * is structurally safe (a `SigVar` maps to a by-name `ParameterRef`, never its bound; `collectInherited`
 * carries a visited-set), and this test pins that: extraction must terminate, hash deterministically,
 * and agree with reflection on declared/inherited member names.
 */
object FBoundStressSpecification extends Properties:
  override def tests: List[Test] = List(
    example(
      "ClassfileToAPI on the F-bound stress corpus should terminate and hash deterministically",
      terminatesDeterministically
    ),
    example(
      "agree with ClassToAPI (reflection) on declared and inherited member names",
      agreesWithReflection
    ),
  )

  // The corpus lives in test resources so the guard runs hermetically. One multi-type .java file
  // (one public type, the rest package-private).
  private def corpusSource: String =
    val url = Option(getClass.getClassLoader.getResource("fbound/FBoundedStress.java"))
      .getOrElse(sys.error("fbound/FBoundedStress.java missing from test resources"))
    IO.readStream(url.openStream())

  // Compile the whole corpus once and return (output dir, every compiled class as name -> ClassFile).
  private def compileCorpus(temp: File): (File, Seq[(String, ClassFile)]) =
    val dir = new File(temp, "classes")
    dir.mkdir()
    val src = new File(temp, "FBoundedStress.java")
    IO.write(src, corpusSource)
    JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
    val classes = (dir.listFiles().toSeq.filter(_.getName.endsWith(".class"))).map { f =>
      val cf = Parser(f.toPath, Logger.Null)
      cf.className -> cf
    }
    (dir, classes)

  private def corpusCompiled(classes: Seq[(String, ClassFile)]): Result =
    Result
      .assert(classes.sizeIs > 20)
      .log(s"expected the full corpus to compile, got ${classes.map(_._1)}")

  // Byte-only parent resolver (no class loading), exactly as production uses under classfileJavaApi.
  private def byteResolver(dir: File): String => Option[ClassFile] = binaryName =>
    val res = binaryName.replace('.', '/') + ".class"
    val loader = new URLClassLoader(Array(dir.toURI.toURL), null)
    Option(loader.getResource(res))
      .orElse(Option(ClassLoader.getSystemResource(res)))
      .flatMap(u =>
        try Some(Parser(u, Logger.Null))
        catch case _: Throwable => None
      )

  private def terminatesDeterministically: Result = IO.withTemporaryDirectory { temp =>
    val (dir, classes) = compileCorpus(temp)
    val resolve = byteResolver(dir)

    // A non-terminating walk would hang the suite; bound it so a cycle bug surfaces as a failure.
    def hashAll(): Int =
      Await.result(
        Future(ClassfileToAPI.process(classes, resolve)._1.map(HashAPI(_)).sum),
        30.seconds
      )

    val h1 = hashAll()
    val h2 = hashAll()
    Result.all(
      List(
        corpusCompiled(classes),
        (h1 ==== h2).log("hash must be deterministic across runs over cyclic F-bound signatures"),
      )
    )
  }

  private def agreesWithReflection: Result = IO.withTemporaryDirectory { temp =>
    val (dir, classes) = compileCorpus(temp)
    val resolve = byteResolver(dir)
    val loader = new URLClassLoader(Array(dir.toURI.toURL), null)

    val classfileApis = ClassfileToAPI.process(classes, resolve)._1
    def declared(c: ClassLike): Set[String] = c.structure.declared.map(_.name).toSet
    def inherited(c: ClassLike): Set[String] = c.structure.inherited.map(_.name).toSet

    val failures = scala.collection.mutable.ListBuffer.empty[String]
    for (binaryName, _) <- classes do
      // Reflection keys by canonical name; the classfile API does too. Skip anything reflection
      // can't model cleanly (anonymous classes have no canonical name) to keep the comparison fair.
      val loaded =
        try Some(loader.loadClass(binaryName))
        catch case _: Throwable => None
      loaded.filter(_.getCanonicalName != null).foreach { cls =>
        val name = cls.getCanonicalName
        val reflect = ClassToAPI.process(Seq(cls))._1.filter(_.name == name)
        val classfile = classfileApis.filter(_.name == name)
        if reflect.nonEmpty && classfile.nonEmpty then
          val dR = reflect.flatMap(declared).toSet
          val dC = classfile.flatMap(declared).toSet
          val iR = reflect.flatMap(inherited).toSet
          val iC = classfile.flatMap(inherited).toSet
          if dR != dC then
            failures += s"$name declared: onlyReflect=${dR -- dC} onlyClassfile=${dC -- dR}"
          if iR != iC then
            failures += s"$name inherited: onlyReflect=${iR -- iC} onlyClassfile=${iC -- iR}"
      }
    Result.all(
      List(
        corpusCompiled(classes),
        Result
          .assert(failures.isEmpty)
          .log("divergences from reflection:\n" + failures.mkString("\n")),
      )
    )
  }
end FBoundStressSpecification
