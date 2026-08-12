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

import java.io.File
import java.nio.file.Paths
import java.util.Optional

import sbt.internal.inc.{ MixedAnalyzingCompiler, PlainVirtualFileConverter, UnitSpec }
import xsbti.VirtualFile
import xsbti.compile._

/**
 * The search classpath drives dependency lookup, so it has to describe the same class
 * universe the compiler sees. See sbt/zinc#348.
 */
class SearchClasspathSpec extends UnitSpec {
  private val scalaLibraryJar = new File("/tmp/zinc-test/scala-library.jar")
  private val userBootJar = "/tmp/zinc-test/custom-library.jar"
  private val converter = PlainVirtualFileConverter.converter

  private object FakeScalaInstance extends ScalaInstance {
    val version = "2.12.20"
    val actualVersion = version
    val loader = getClass.getClassLoader
    val loaderCompilerOnly = loader
    val loaderLibraryOnly = loader
    val libraryJars = Array(scalaLibraryJar)
    val compilerJars = Array.empty[File]
    val otherJars = Array.empty[File]
    val allJars = Array(scalaLibraryJar)
  }

  private object FakeCompiler extends ScalaCompiler {
    def scalaInstance: ScalaInstance = FakeScalaInstance
    def classpathOptions: ClasspathOptions = ClasspathOptionsUtil.boot()
    def compile(
        sources: Array[VirtualFile],
        classpath: Array[VirtualFile],
        converter: xsbti.FileConverter,
        changes: xsbti.compile.DependencyChanges,
        options: Array[String],
        output: Output,
        callback: xsbti.AnalysisCallback,
        reporter: xsbti.Reporter,
        progress: Optional[CompileProgress],
        log: xsbti.Logger
    ): Unit = ()
  }

  private object NoLookup extends PerClasspathEntryLookup {
    def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] = Optional.empty()
    def definesClass(classpathEntry: VirtualFile): DefinesClass = _ => false
  }

  private def searchClasspath(scalacOptions: Array[String]): Seq[String] = {
    val classpath = Seq(converter.toVirtualFile(scalaLibraryJar.toPath))
    MixedAnalyzingCompiler
      .searchClasspathAndLookup(converter, classpath, scalacOptions, NoLookup, FakeCompiler)
      ._1
      .map(converter.toPath(_).toString)
  }

  "the search classpath" should "include Zinc's boot classpath when the client sets none" in {
    val entries = searchClasspath(Array("-deprecation"))
    assert(entries.contains(scalaLibraryJar.toString))
  }

  it should "use the client's boot classpath instead of Zinc's when one is set" in {
    val entries = searchClasspath(Array("-bootclasspath", userBootJar))
    assert(entries.contains(Paths.get(userBootJar).toString))
    assert(!entries.contains(scalaLibraryJar.toString))
  }

  it should "recognize every spelling the compiler accepts" in {
    val spellings = Seq(
      Array("-bootclasspath", userBootJar),
      Array(s"-bootclasspath:$userBootJar"),
      Array("--boot-class-path", userBootJar),
      Array(s"--boot-class-path:$userBootJar"),
    )
    spellings.foreach { options =>
      assert(!searchClasspath(options).contains(scalaLibraryJar.toString), options.mkString(" "))
    }
  }
}
