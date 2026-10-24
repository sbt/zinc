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
import java.io.File
import java.nio.file.Paths
import java.util.Optional

import sbt.internal.inc.{ MixedAnalyzingCompiler, PlainVirtualFileConverter }
import xsbti.VirtualFile
import xsbti.compile.*

/**
 * The search classpath drives dependency lookup, so it has to describe the same class
 * universe the compiler sees. See sbt/zinc#348.
 */
object SearchClasspathSpec extends Properties:
  override def tests: List[Test] = List(
    example(
      "the search classpath should include Zinc's boot classpath when the client sets none",
      zincBootClasspath
    ),
    example(
      "use the client's boot classpath instead of Zinc's when one is set",
      clientBootClasspath
    ),
    example("recognize every spelling the compiler accepts", everySpelling),
    property("use any boot classpath the client sets among other options", propClientBoot),
  )

  // Absolute so that assertions match the absolutized entries on Windows too,
  // where "/tmp/..." is only drive-relative.
  private val scalaLibraryJar = new File("/tmp/zinc-test/scala-library.jar").getAbsoluteFile
  private val userBootJar = "/tmp/zinc-test/custom-library.jar"
  private val converter = PlainVirtualFileConverter.converter

  private object FakeScalaInstance extends ScalaInstance:
    val version = "2.12.20"
    val actualVersion = version
    val loader = getClass.getClassLoader
    val loaderCompilerOnly = loader
    val loaderLibraryOnly = loader
    val libraryJars = Array(scalaLibraryJar)
    val compilerJars = Array.empty[File]
    val otherJars = Array.empty[File]
    val allJars = Array(scalaLibraryJar)

  private object FakeCompiler extends ScalaCompiler:
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

  private object NoLookup extends PerClasspathEntryLookup:
    def analysis(classpathEntry: VirtualFile): Optional[CompileAnalysis] = Optional.empty()
    def definesClass(classpathEntry: VirtualFile): DefinesClass = _ => false

  private def searchClasspath(scalacOptions: Array[String]): Seq[String] =
    val classpath = Seq(converter.toVirtualFile(scalaLibraryJar.toPath))
    MixedAnalyzingCompiler
      .searchClasspathAndLookup(converter, classpath, scalacOptions, NoLookup, FakeCompiler)
      ._1
      .map(converter.toPath(_).toString)

  private def zincBootClasspath: Result =
    val entries = searchClasspath(Array("-deprecation"))
    Result.assert(entries.contains(scalaLibraryJar.toString)).log(s"entries: $entries")

  private def clientBootClasspath: Result =
    val entries = searchClasspath(Array("-bootclasspath", userBootJar))
    Result.all(
      List(
        Result.assert(entries.contains(Paths.get(userBootJar).toString)),
        Result.assert(!entries.contains(scalaLibraryJar.toString)),
      )
    ).log(s"entries: $entries")

  private val spellings: List[String => Array[String]] = List(
    path => Array("-bootclasspath", path),
    path => Array(s"-bootclasspath:$path"),
    path => Array("--boot-class-path", path),
    path => Array(s"--boot-class-path:$path"),
  )

  private def everySpelling: Result =
    Result.all(spellings.map { spell =>
      val options = spell(userBootJar)
      Result
        .assert(!searchClasspath(options).contains(scalaLibraryJar.toString))
        .log(options.mkString(" "))
    })

  def propClientBoot: Property =
    for
      spell <- Gen.elementUnsafe(spellings).forAll
      name <- Gen.string(Gen.alphaNum, Range.linear(1, 8)).forAll
      others <- Gen
        .list(Gen.element1("-deprecation", "-feature", "-unchecked"), Range.linear(0, 3))
        .forAll
    yield
      val bootJar = new File(s"/tmp/zinc-test/$name.jar").getAbsoluteFile.toString
      val options = (others ++ spell(bootJar)).toArray
      val entries = searchClasspath(options)
      Result
        .all(
          List(
            Result.assert(entries.contains(Paths.get(bootJar).toString)),
            Result.assert(!entries.contains(scalaLibraryJar.toString)),
          )
        )
        .log(s"options: ${options.mkString(" ")}, entries: $entries")
end SearchClasspathSpec
