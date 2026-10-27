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
import xsbti.compile.{ IncOptions, Inputs, Output }

import scala.jdk.CollectionConverters.*

/**
 * sbt/zinc#586: javac products that already exist in the output directory but are not tracked by
 * the previous analysis (shared output dir, lost analysis, or a run that failed after javac wrote
 * them) must still be attributed to their source. An anonymous class regenerated alongside such a
 * top-level class is filed under the top-level class name, so if that class's API is never read
 * the callback crashes with "Failed to find name hashes".
 */
object JavaPreexistingClassfileSpec extends Properties with BaseCompilerSpec:
  override def tests: List[Test] = List(
    example(
      "analyze a Java source whose top-level class file pre-exists untracked",
      check(deleteAnonymous = true, noPickleJava)
    ),
    example(
      "record products when all class files of a Java source pre-exist untracked",
      check(deleteAnonymous = false, noPickleJava)
    ),
    example("not analyze the Scala products sharing the output directory", scalaProducts),
    example("record products of a Java source compiled straight to a jar", straightToJar),
    example(
      "analyze a pre-existing top-level Java class under -Ypickle-java",
      check(deleteAnonymous = true, identity)
    ),
    example(
      "record products of pre-existing Java class files under -Ypickle-java",
      check(deleteAnonymous = false, identity)
    ),
  )

  private val javaSrc =
    """public class A {
      |  public Runnable r() { return new Runnable() { public void run() {} }; }
      |}
      |""".stripMargin

  // sbt's default: no pipelining, so no -Ypickle-java and no early output. Under -Ypickle-java
  // scalac also reports the Java classes' API and products.
  private val noPickleJava: Inputs => Inputs = in =>
    in.withOptions(in.options.withScalacOptions(Array()).withEarlyOutput(Optional.empty[Output]()))

  /** Stale class files are old; make sure the test never depends on timestamp resolution. */
  private def backdate(dir: Path): Unit =
    val stream = Files.walk(dir)
    try
      stream.iterator.asScala.filter(_.toString.endsWith(".class")).foreach { p =>
        val tenSecondsAgo = FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis - 10000)
        Files.setLastModifiedTime(p, tenSecondsAgo)
      }
    finally stream.close()

  private def apiHash(a: Analysis): Option[Int] = a.apis.internal.get("A").map(_.apiHash)

  private def check(deleteAnonymous: Boolean, newInputs: Inputs => Inputs): Result =
    IO.withTemporaryDirectory: tempDir =>
      val baseDir = tempDir.toPath
      Files.write(baseDir.resolve("A.java"), javaSrc.getBytes("UTF-8"))
      val projectSetup = ProjectSetup.simple(baseDir, Seq("A.java"))
      withCompiler(projectSetup.createCompiler(scalaVersion, IncOptions.of())): comp =>
        val classesDir = projectSetup.classesDir
        val src = comp.sources.find(_.name == "A.java").get
        // empty previous analysis, fresh output dir
        val clean = comp.doCompile(newInputs).analysis.asInstanceOf[Analysis]
        val cleanOutput = Result.all(
          List(
            assertExists(classesDir.resolve("A.class")),
            assertExists(classesDir.resolve("A$1.class")),
            clean.relations.products(src).size ==== 2,
          )
        )
        // A.class stays on disk, untracked by the (again empty) previous analysis
        if deleteAnonymous then Files.delete(classesDir.resolve("A$1.class"))
        backdate(classesDir)
        val analysis = comp.doCompile(newInputs).analysis.asInstanceOf[Analysis]
        Result.all(
          List(
            cleanOutput,
            Result.assert(analysis.relations.products(src).nonEmpty).log("A.java has no products"),
            Result.assert(analysis.apis.internal.contains("A")).log("A has no API"),
            analysis.relations.products(src) ==== clean.relations.products(src),
            analysis.apis.internal.keySet ==== clean.apis.internal.keySet,
            apiHash(analysis) ==== apiHash(clean),
            analysis.relations.allSources ==== clean.relations.allSources,
          )
        )

  private def scalaProducts: Result =
    IO.withTemporaryDirectory: tempDir =>
      val baseDir = tempDir.toPath
      Files.write(baseDir.resolve("A.java"), javaSrc.getBytes("UTF-8"))
      Files.write(baseDir.resolve("S.scala"), "class S { def a = new A }".getBytes("UTF-8"))
      val projectSetup = ProjectSetup.simple(baseDir, Seq("A.java", "S.scala"))
      withCompiler(projectSetup.createCompiler(scalaVersion, IncOptions.of())): comp =>
        val clean = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        backdate(projectSetup.classesDir)
        // scalac rewrites its own products before the javac snapshot is taken, so they must not
        // be picked up as javac products of A.java.
        val analysis = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        val javaSource = comp.sources.find(_.name == "A.java").get
        Result.all(
          List(
            analysis.relations.allProducts ==== clean.relations.allProducts,
            analysis.apis.internal.keySet ==== clean.apis.internal.keySet,
            analysis.relations.products(javaSource) ==== clean.relations.products(javaSource),
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
        // withPreviousJar moves the jar aside, so this snapshot is empty like the first one; the
        // case guards that path rather than reproducing sbt/zinc#586.
        val again = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        Result.all(
          List(
            clean.relations.products(src).size ==== 2,
            Result.assert(clean.apis.internal.contains("A")).log("A has no API"),
            // javac writes into the jar itself, so JarClassFinder is what snapshots the output
            Result
              .assert(clean.relations.products(src).forall(_.id.contains("output.jar")))
              .log(s"products outside the jar: ${clean.relations.products(src)}"),
            again.relations.products(src) ==== clean.relations.products(src),
            Result.assert(again.apis.internal.contains("A")).log("A has no API after a recompile"),
          )
        )
end JavaPreexistingClassfileSpec
