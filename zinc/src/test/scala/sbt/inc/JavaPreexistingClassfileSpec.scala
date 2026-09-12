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
class JavaPreexistingClassfileSpec extends BaseCompilerSpec {
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
  private def backdate(dir: Path): Unit = {
    val stream = Files.walk(dir)
    try
      stream.iterator.asScala.filter(_.toString.endsWith(".class")).foreach { p =>
        val tenSecondsAgo = FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis - 10000)
        Files.setLastModifiedTime(p, tenSecondsAgo)
      }
    finally stream.close()
  }

  private def check(deleteAnonymous: Boolean, newInputs: Inputs => Inputs): Unit =
    IO.withTemporaryDirectory { tempDir =>
      val baseDir = tempDir.toPath
      Files.write(baseDir.resolve("A.java"), javaSrc.getBytes("UTF-8"))
      val projectSetup = ProjectSetup.simple(baseDir, Seq("A.java"))
      val comp = projectSetup.createCompiler(scalaVersion, IncOptions.of())
      try {
        val classesDir = projectSetup.classesDir
        val src = comp.sources.find(_.name == "A.java").get
        // empty previous analysis, fresh output dir
        val clean = comp.doCompile(newInputs).analysis.asInstanceOf[Analysis]
        assertExists(classesDir.resolve("A.class"))
        assertExists(classesDir.resolve("A$1.class"))
        assert(clean.relations.products(src).size == 2)
        // A.class stays on disk, untracked by the (again empty) previous analysis
        if (deleteAnonymous) Files.delete(classesDir.resolve("A$1.class"))
        backdate(classesDir)
        val analysis = comp.doCompile(newInputs).analysis.asInstanceOf[Analysis]
        assert(analysis.relations.products(src).nonEmpty, "A.java has no products")
        assert(analysis.apis.internal.contains("A"))
        assert(analysis.relations.products(src) == clean.relations.products(src))
        assert(analysis.apis.internal.keySet == clean.apis.internal.keySet)
        assert(analysis.apis.internalAPI("A").apiHash == clean.apis.internalAPI("A").apiHash)
        assert(analysis.relations.allSources == clean.relations.allSources)
      } finally comp.close()
    }

  it should "analyze a Java source whose top-level class file pre-exists untracked" in {
    check(deleteAnonymous = true, noPickleJava)
  }

  it should "record products when all class files of a Java source pre-exist untracked" in {
    check(deleteAnonymous = false, noPickleJava)
  }

  it should "not analyze the Scala products sharing the output directory" in {
    IO.withTemporaryDirectory { tempDir =>
      val baseDir = tempDir.toPath
      Files.write(baseDir.resolve("A.java"), javaSrc.getBytes("UTF-8"))
      Files.write(baseDir.resolve("S.scala"), "class S { def a = new A }".getBytes("UTF-8"))
      val projectSetup = ProjectSetup.simple(baseDir, Seq("A.java", "S.scala"))
      val comp = projectSetup.createCompiler(scalaVersion, IncOptions.of())
      try {
        val clean = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        backdate(projectSetup.classesDir)
        // scalac rewrites its own products before the javac snapshot is taken, so they must not
        // be picked up as javac products of A.java.
        val analysis = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        assert(analysis.relations.allProducts == clean.relations.allProducts)
        assert(analysis.apis.internal.keySet == clean.apis.internal.keySet)
        val javaSource = comp.sources.find(_.name == "A.java").get
        assert(analysis.relations.products(javaSource) == clean.relations.products(javaSource))
      } finally comp.close()
    }
  }

  it should "record products of a Java source compiled straight to a jar" in {
    IO.withTemporaryDirectory { tempDir =>
      val baseDir = tempDir.toPath
      Files.write(baseDir.resolve("A.java"), javaSrc.getBytes("UTF-8"))
      val projectSetup = ProjectSetup.simple(baseDir, Seq("A.java")).copy(outputToJar = true)
      val comp = projectSetup.createCompiler(scalaVersion, IncOptions.of())
      try {
        val src = comp.sources.find(_.name == "A.java").get
        val clean = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        assert(clean.relations.products(src).size == 2)
        assert(clean.apis.internal.contains("A"))
        // javac writes into the jar itself, so JarClassFinder is what snapshots the output
        assert(clean.relations.products(src).forall(_.id.contains("output.jar")))
        // withPreviousJar moves the jar aside, so this snapshot is empty like the first one; the
        // case guards that path rather than reproducing sbt/zinc#586.
        val again = comp.doCompile(noPickleJava).analysis.asInstanceOf[Analysis]
        assert(again.relations.products(src) == clean.relations.products(src))
        assert(again.apis.internal.contains("A"))
      } finally comp.close()
    }
  }

  it should "analyze a pre-existing top-level Java class under -Ypickle-java" in {
    check(deleteAnonymous = true, identity)
  }

  it should "record products of pre-existing Java class files under -Ypickle-java" in {
    check(deleteAnonymous = false, identity)
  }
}
