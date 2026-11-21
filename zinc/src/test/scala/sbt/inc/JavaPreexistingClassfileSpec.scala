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

package sbt.inc

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.nio.file.{ Files, Path }
import java.nio.file.attribute.FileTime
import java.util.Optional

import sbt.internal.inc.Analysis
import sbt.io.IO
import xsbti.compile.{ CompileResult, IncOptions, Inputs, Output }

import scala.jdk.CollectionConverters.*

/**
 * sbt/zinc#586: class files that javac rewrites but the previous analysis does not track are still
 * products of their source. Otherwise the compile crashes, or it records no products.
 */
object JavaPreexistingClassfileSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example(
      "analyze a Java source whose top-level class file pre-exists untracked",
      check(deleteAnonymous = true, _.doCompile(noPickleJava))
    ),
    example(
      "record products when all class files of a Java source pre-exist untracked",
      check(deleteAnonymous = false, _.doCompile(noPickleJava))
    ),
    example(
      "analyze a pre-existing top-level Java class under -Ypickle-java",
      check(deleteAnonymous = true, _.doCompile())
    ),
    example(
      "record products of pre-existing Java class files under -Ypickle-java",
      check(deleteAnonymous = false, _.doCompile())
    ),
    example(
      "analyze a pre-existing top-level Java class in the pipelined Java step",
      check(deleteAnonymous = true, _.doCompileAllJava())
    ),
    example("record products of a Java source compiled straight to a jar", straightToJar),
  )

  private val javaSrc =
    """public class A {
      |  public Runnable r() { return new Runnable() { public void run() {} }; }
      |}
      |""".stripMargin

  /** sbt's default: no pipelining, so no `-Ypickle-java` and no early output. */
  private val noPickleJava: Inputs => Inputs = in =>
    in.withOptions(in.options.withScalacOptions(Array()).withEarlyOutput(Optional.empty[Output]()))

  /** Untracked class files are old, so the test never depends on timestamp resolution. */
  private def backdate(dir: Path): Unit =
    val stream = Files.walk(dir)
    try
      stream.iterator.asScala.filter(_.toString.endsWith(".class")).foreach { p =>
        val tenSecondsAgo = FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis - 10000)
        Files.setLastModifiedTime(p, tenSecondsAgo)
      }
    finally stream.close()

  private def apiHash(a: Analysis): Option[Int] = a.apis.internal.get("A").map(_.apiHash)

  private def check(deleteAnonymous: Boolean, compile: CompilerSetup => CompileResult): Result =
    IO.withTemporaryDirectory: tempDir =>
      val baseDir = tempDir.toPath
      Files.write(baseDir.resolve("A.java"), javaSrc.getBytes("UTF-8"))
      val projectSetup = ProjectSetup.simple(baseDir, Seq("A.java"))
      withCompiler(projectSetup.createCompiler(scalaVersion, IncOptions.of())): comp =>
        val classesDir = projectSetup.classesDir
        val src = comp.sources.find(_.name == "A.java").get
        val clean = compile(comp).analysis.asInstanceOf[Analysis]
        val cleanOutput = Result.all(
          List(
            assertExists(classesDir.resolve("A.class")),
            assertExists(classesDir.resolve("A$1.class")),
            clean.relations.products(src).size ==== 2,
          )
        )
        if deleteAnonymous then Files.delete(classesDir.resolve("A$1.class"))
        backdate(classesDir)
        val analysis = compile(comp).analysis.asInstanceOf[Analysis]
        Result.all(
          List(
            cleanOutput,
            analysis.relations.products(src) ==== clean.relations.products(src),
            analysis.apis.internal.keySet ==== clean.apis.internal.keySet,
            apiHash(analysis) ==== apiHash(clean),
            analysis.relations.allSources ==== clean.relations.allSources,
          )
        )

  private def straightToJar: Result =
    IO.withTemporaryDirectory: tempDir =>
      val baseDir = tempDir.toPath
      Files.write(baseDir.resolve("A.java"), javaSrc.getBytes("UTF-8"))
      val projectSetup = ProjectSetup.simple(baseDir, Seq("A.java")).copy(outputToJar = true)
      withCompiler(projectSetup.createCompiler(scalaVersion, IncOptions.of())): comp =>
        val src = comp.sources.find(_.name == "A.java").get
        val clean = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        val again = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        Result.all(
          List(
            clean.relations.products(src).size ==== 2,
            Result.assert(clean.apis.internal.contains("A")).log("A has no API"),
            Result
              .assert(clean.relations.products(src).forall(_.id.contains("output.jar")))
              .log(s"products outside the jar: ${clean.relations.products(src)}"),
            again.relations.products(src) ==== clean.relations.products(src),
            Result.assert(again.apis.internal.contains("A")).log("A has no API after a recompile"),
          )
        )
end JavaPreexistingClassfileSpec
