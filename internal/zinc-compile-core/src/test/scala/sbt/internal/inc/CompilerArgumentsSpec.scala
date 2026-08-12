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
import java.util.function.Supplier

import org.scalatest.flatspec.AnyFlatSpec
import xsbti.compile.{ ClasspathOptions, ClasspathOptionsUtil }

class CompilerArgumentsSpec extends AnyFlatSpec {
  import CompilerArguments.{ BootClasspathLongOption, BootClasspathOption }

  private val scalaLibraryJar = new File("scala-library.jar")

  private object FakeScalaInstance extends xsbti.compile.ScalaInstance {
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

  private class RecordingLogger extends xsbti.Logger {
    val warnings = collection.mutable.Buffer.empty[String]
    def warn(msg: Supplier[String]): Unit = warnings += msg.get()
    def error(msg: Supplier[String]): Unit = ()
    def info(msg: Supplier[String]): Unit = ()
    def debug(msg: Supplier[String]): Unit = ()
    def trace(exception: Supplier[Throwable]): Unit = ()
  }

  private val autoBootOptions = ClasspathOptionsUtil.boot()
  private val noAutoBootOptions = ClasspathOptions.of(false, false, false, false, false)

  private def makeArguments(cpOptions: ClasspathOptions, options: Seq[String], log: xsbti.Logger) =
    new CompilerArguments(FakeScalaInstance, cpOptions)
      .makeArguments(Nil, Seq(scalaLibraryJar.toPath), options, log)

  "makeArguments" should "add a boot classpath containing the Scala library when autoBoot is set" in {
    val log = new RecordingLogger
    val args = makeArguments(autoBootOptions, Seq("-deprecation"), log)
    assert(args.count(_ == BootClasspathOption) == 1)
    val value = args(args.indexOf(BootClasspathOption) + 1)
    assert(value.endsWith(scalaLibraryJar.getName))
    assert(log.warnings.isEmpty)
  }

  it should "keep a user-provided boot classpath and warn instead of overriding it" in {
    val log = new RecordingLogger
    val userOptions = Seq(BootClasspathOption, "/custom/rt.jar")
    val args = makeArguments(autoBootOptions, userOptions, log)
    assert(args.count(_ == BootClasspathOption) == 1)
    assert(args(args.indexOf(BootClasspathOption) + 1) == "/custom/rt.jar")
    assert(log.warnings.size == 1)
    assert(log.warnings.head.contains("Scala library"))
  }

  it should "keep a boot classpath given in the colon form and warn instead of overriding it" in {
    val log = new RecordingLogger
    val userOptions = Seq(s"$BootClasspathOption:/custom/rt.jar")
    val args = makeArguments(autoBootOptions, userOptions, log)
    assert(!args.contains(BootClasspathOption))
    assert(args.contains(s"$BootClasspathOption:/custom/rt.jar"))
    assert(log.warnings.size == 1)
  }

  it should "keep a boot classpath given with the long option and warn instead of overriding it" in {
    val log = new RecordingLogger
    val userOptions = Seq(BootClasspathLongOption, "/custom/rt.jar")
    val args = makeArguments(autoBootOptions, userOptions, log)
    assert(!args.contains(BootClasspathOption))
    assert(args.count(_ == BootClasspathLongOption) == 1)
    assert(log.warnings.size == 1)
  }

  it should "not add a boot classpath when autoBoot is not set" in {
    val log = new RecordingLogger
    val args = makeArguments(noAutoBootOptions, Seq("-deprecation"), log)
    assert(!args.contains(BootClasspathOption))
    assert(log.warnings.isEmpty)
  }

  it should "not warn about a user-provided boot classpath when autoBoot is not set" in {
    val log = new RecordingLogger
    val userOptions = Seq(BootClasspathOption, "/custom/rt.jar")
    val args = makeArguments(noAutoBootOptions, userOptions, log)
    assert(args.count(_ == BootClasspathOption) == 1)
    assert(args(args.indexOf(BootClasspathOption) + 1) == "/custom/rt.jar")
    assert(log.warnings.isEmpty)
  }

  "explicitBootClasspath" should "find none when no boot classpath is given" in {
    assert(CompilerArguments.explicitBootClasspath(Seq("-deprecation")).isEmpty)
  }

  it should "find a boot classpath in every spelling scalac accepts" in {
    // Verified against scalac 2.13 and 3: these four are accepted, and the two
    // near-misses below are rejected by both compilers.
    val accepted = Seq(
      Seq(BootClasspathOption, "/a.jar"),
      Seq(s"$BootClasspathOption:/a.jar"),
      Seq(BootClasspathLongOption, "/a.jar"),
      Seq(s"$BootClasspathLongOption:/a.jar"),
    )
    accepted.foreach(options =>
      assert(CompilerArguments.explicitBootClasspath(options) == Some("/a.jar"), options)
    )
  }

  it should "ignore spellings that scalac itself rejects" in {
    val rejected = Seq(Seq("-boot-class-path", "/a.jar"), Seq("--bootclasspath", "/a.jar"))
    rejected.foreach(options =>
      assert(CompilerArguments.explicitBootClasspath(options).isEmpty, options)
    )
  }

  it should "take the last occurrence, as scalac does" in {
    val options = Seq(BootClasspathOption, "/first.jar", s"$BootClasspathOption:/second.jar")
    assert(CompilerArguments.explicitBootClasspath(options) == Some("/second.jar"))
  }

  it should "not confuse another option that merely starts with the same text" in {
    assert(CompilerArguments.explicitBootClasspath(Seq("-bootclasspathological")).isEmpty)
  }
}
