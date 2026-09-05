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
import java.net.URLClassLoader
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
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
class FBoundStressSpecification extends UnitSpec {

  // The corpus lives in test resources so the guard runs hermetically. One multi-type .java file
  // (one public type, the rest package-private).
  private def corpusSource: String = {
    val url = getClass.getClassLoader.getResource("fbound/FBoundedStress.java")
    assert(url != null, "fbound/FBoundedStress.java missing from test resources")
    IO.readStream(url.openStream())
  }

  // Compile the whole corpus once and return (output dir, every compiled class as name -> ClassFile).
  private def compileCorpus(temp: File): (File, Seq[(String, ClassFile)]) = {
    val dir = new File(temp, "classes")
    dir.mkdir()
    val src = new File(temp, "FBoundedStress.java")
    IO.write(src, corpusSource)
    JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
    val classes = (dir.listFiles().toSeq.filter(_.getName.endsWith(".class"))).map { f =>
      val cf = Parser(f.toPath, Logger.Null)
      cf.className -> cf
    }
    assert(classes.sizeIs > 20, s"expected the full corpus to compile, got ${classes.map(_._1)}")
    (dir, classes)
  }

  // Byte-only parent resolver (no class loading), exactly as production uses under classfileJavaApi.
  private def byteResolver(dir: File): String => Option[ClassFile] = binaryName => {
    val res = binaryName.replace('.', '/') + ".class"
    val loader = new URLClassLoader(Array(dir.toURI.toURL), null)
    Option(loader.getResource(res))
      .orElse(Option(ClassLoader.getSystemResource(res)))
      .flatMap(u =>
        try Some(Parser(u, Logger.Null))
        catch { case _: Throwable => None }
      )
  }

  "ClassfileToAPI on the F-bound stress corpus" should "terminate and hash deterministically" in {
    IO.withTemporaryDirectory { temp =>
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
      assert(h1 == h2, "hash must be deterministic across runs over cyclic F-bound signatures")
    }
  }

  it should "agree with ClassToAPI (reflection) on declared and inherited member names" in {
    IO.withTemporaryDirectory { temp =>
      val (dir, classes) = compileCorpus(temp)
      val resolve = byteResolver(dir)
      val loader = new URLClassLoader(Array(dir.toURI.toURL), null)

      val classfileApis = ClassfileToAPI.process(classes, resolve)._1
      def declared(c: ClassLike): Set[String] = c.structure.declared.map(_.name).toSet
      def inherited(c: ClassLike): Set[String] = c.structure.inherited.map(_.name).toSet

      val failures = scala.collection.mutable.ListBuffer.empty[String]
      for ((binaryName, _) <- classes) {
        // Reflection keys by canonical name; the classfile API does too. Skip anything reflection
        // can't model cleanly (anonymous classes have no canonical name) to keep the comparison fair.
        val loaded =
          try Some(loader.loadClass(binaryName))
          catch { case _: Throwable => None }
        loaded.filter(_.getCanonicalName != null).foreach { cls =>
          val name = cls.getCanonicalName
          val reflect = ClassToAPI.process(Seq(cls))._1.filter(_.name == name)
          val classfile = classfileApis.filter(_.name == name)
          if (reflect.nonEmpty && classfile.nonEmpty) {
            val dR = reflect.flatMap(declared).toSet
            val dC = classfile.flatMap(declared).toSet
            val iR = reflect.flatMap(inherited).toSet
            val iC = classfile.flatMap(inherited).toSet
            if (dR != dC)
              failures += s"$name declared: onlyReflect=${dR -- dC} onlyClassfile=${dC -- dR}"
            if (iR != iC)
              failures += s"$name inherited: onlyReflect=${iR -- iC} onlyClassfile=${iC -- iR}"
          }
        }
      }
      assert(failures.isEmpty, "divergences from reflection:\n" + failures.mkString("\n"))
    }
  }
}
