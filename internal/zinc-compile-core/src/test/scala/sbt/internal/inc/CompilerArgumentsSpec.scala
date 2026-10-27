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
import java.util.function.Supplier
import xsbti.compile.{ ClasspathOptions, ClasspathOptionsUtil }

object CompilerArgumentsSpec extends Properties:
  import CompilerArguments.{ BootClasspathLongOption, BootClasspathOption }

  override def tests: List[Test] = List(
    example(
      "makeArguments should add a boot classpath containing the Scala library when autoBoot is set",
      addsBootClasspath
    ),
    example("keep a user-provided boot classpath and warn instead of overriding it", keepsUserBoot),
    example(
      "keep a boot classpath given in the colon form and warn instead of overriding it",
      keepsColonForm
    ),
    example(
      "keep a boot classpath given with the long option and warn instead of overriding it",
      keepsLongOption
    ),
    example("not add a boot classpath when autoBoot is not set", noAutoBoot),
    example(
      "not warn about a user-provided boot classpath when autoBoot is not set",
      noAutoBootNoWarning
    ),
    property("keep any user-provided boot classpath and warn once", propKeepsUserBoot),
    example("explicitBootClasspath should find none when no boot classpath is given", findsNone),
    example("find a boot classpath in every spelling scalac accepts", everySpelling),
    example("ignore spellings that scalac itself rejects", rejectedSpellings),
    example("take the last occurrence, as scalac does", lastOccurrence),
    example("not confuse another option that merely starts with the same text", similarOption),
    property("find the last boot classpath among any other options", propLastOccurrence),
  )

  private val scalaLibraryJar = new File("scala-library.jar")

  private object FakeScalaInstance extends xsbti.compile.ScalaInstance:
    val version = "2.12.20"
    val actualVersion = version
    val loader = getClass.getClassLoader
    val loaderCompilerOnly = loader
    val loaderLibraryOnly = loader
    val libraryJars = Array(scalaLibraryJar)
    val compilerJars = Array.empty[File]
    val otherJars = Array.empty[File]
    val allJars = Array(scalaLibraryJar)

  private class RecordingLogger extends xsbti.Logger:
    val warnings = collection.mutable.Buffer.empty[String]
    def warn(msg: Supplier[String]): Unit = warnings += msg.get()
    def error(msg: Supplier[String]): Unit = ()
    def info(msg: Supplier[String]): Unit = ()
    def debug(msg: Supplier[String]): Unit = ()
    def trace(exception: Supplier[Throwable]): Unit = ()

  private val autoBootOptions = ClasspathOptionsUtil.boot()
  private val noAutoBootOptions = ClasspathOptions.of(false, false, false, false, false)

  private def makeArguments(cpOptions: ClasspathOptions, options: Seq[String], log: xsbti.Logger) =
    new CompilerArguments(FakeScalaInstance, cpOptions)
      .makeArguments(Nil, Seq(scalaLibraryJar.toPath), options, log)

  private def valueOf(args: Seq[String], option: String): Option[String] =
    args.lift(args.indexOf(option) + 1)

  private def addsBootClasspath: Result =
    val log = new RecordingLogger
    val args = makeArguments(autoBootOptions, Seq("-deprecation"), log)
    Result.all(
      List(
        args.count(_ == BootClasspathOption) ==== 1,
        Result.assert(valueOf(
          args,
          BootClasspathOption
        ).exists(_.endsWith(scalaLibraryJar.getName))),
        log.warnings.toList ==== Nil,
      )
    )

  private def keepsUserBoot: Result =
    val log = new RecordingLogger
    val args = makeArguments(autoBootOptions, Seq(BootClasspathOption, "/custom/rt.jar"), log)
    Result.all(
      List(
        args.count(_ == BootClasspathOption) ==== 1,
        valueOf(args, BootClasspathOption) ==== Some("/custom/rt.jar"),
        log.warnings.size ==== 1,
        Result.assert(log.warnings.head.contains("Scala library")).log(log.warnings.head),
      )
    )

  private def keepsColonForm: Result =
    val log = new RecordingLogger
    val args = makeArguments(autoBootOptions, Seq(s"$BootClasspathOption:/custom/rt.jar"), log)
    Result.all(
      List(
        Result.assert(!args.contains(BootClasspathOption)),
        Result.assert(args.contains(s"$BootClasspathOption:/custom/rt.jar")),
        log.warnings.size ==== 1,
      )
    )

  private def keepsLongOption: Result =
    val log = new RecordingLogger
    val args = makeArguments(autoBootOptions, Seq(BootClasspathLongOption, "/custom/rt.jar"), log)
    Result.all(
      List(
        Result.assert(!args.contains(BootClasspathOption)),
        args.count(_ == BootClasspathLongOption) ==== 1,
        log.warnings.size ==== 1,
      )
    )

  private def noAutoBoot: Result =
    val log = new RecordingLogger
    val args = makeArguments(noAutoBootOptions, Seq("-deprecation"), log)
    Result.all(
      List(Result.assert(!args.contains(BootClasspathOption)), log.warnings.toList ==== Nil)
    )

  private def noAutoBootNoWarning: Result =
    val log = new RecordingLogger
    val args = makeArguments(noAutoBootOptions, Seq(BootClasspathOption, "/custom/rt.jar"), log)
    Result.all(
      List(
        args.count(_ == BootClasspathOption) ==== 1,
        valueOf(args, BootClasspathOption) ==== Some("/custom/rt.jar"),
        log.warnings.toList ==== Nil,
      )
    )

  private def findsNone: Result =
    CompilerArguments.explicitBootClasspath(Seq("-deprecation")) ==== None

  // Verified against scalac 2.13 and 3: these four are accepted, and the two
  // near-misses below are rejected by both compilers.
  private val acceptedSpellings: List[String => Seq[String]] = List(
    path => Seq(BootClasspathOption, path),
    path => Seq(s"$BootClasspathOption:$path"),
    path => Seq(BootClasspathLongOption, path),
    path => Seq(s"$BootClasspathLongOption:$path"),
  )

  private def everySpelling: Result =
    Result.all(acceptedSpellings.map { spell =>
      val options = spell("/a.jar")
      (CompilerArguments.explicitBootClasspath(options) ==== Some("/a.jar")).log(s"$options")
    })

  private def rejectedSpellings: Result =
    val rejected = List(Seq("-boot-class-path", "/a.jar"), Seq("--bootclasspath", "/a.jar"))
    Result.all(rejected.map { options =>
      (CompilerArguments.explicitBootClasspath(options) ==== None).log(s"$options")
    })

  private def lastOccurrence: Result =
    val options = Seq(BootClasspathOption, "/first.jar", s"$BootClasspathOption:/second.jar")
    CompilerArguments.explicitBootClasspath(options) ==== Some("/second.jar")

  private def similarOption: Result =
    CompilerArguments.explicitBootClasspath(Seq("-bootclasspathological")) ==== None

  private val genPath: Gen[String] =
    Gen
      .list(Gen.string(Gen.alphaNum, Range.linear(1, 8)), Range.linear(1, 3))
      .map(_.mkString("/", "/", ".jar"))

  private val genBootOption: Gen[Seq[String]] =
    for
      spell <- Gen.elementUnsafe(acceptedSpellings)
      path <- genPath
    yield spell(path)

  private val genOtherOption: Gen[Seq[String]] =
    Gen.choice1(
      Gen.element1(Seq("-deprecation"), Seq("-feature"), Seq("-bootclasspathological")),
      Gen.element1(Seq("-boot-class-path", "/a.jar"), Seq("--bootclasspath", "/a.jar")),
      Gen.string(Gen.lower, Range.linear(1, 8)).map(s => Seq(s"-X$s")),
    )

  private val genOptions: Gen[(List[Seq[String]], Option[String])] =
    for
      groups <- Gen
        .list(Gen.frequency1(3 -> genOtherOption, 1 -> genBootOption), Range.linear(0, 10))
    yield
      val last = groups.reverseIterator.collectFirst {
        case Seq(option, path)
            if option == BootClasspathOption || option == BootClasspathLongOption =>
          path
        case Seq(option) if option.startsWith(s"$BootClasspathOption:") =>
          option.drop(BootClasspathOption.length + 1)
        case Seq(option) if option.startsWith(s"$BootClasspathLongOption:") =>
          option.drop(BootClasspathLongOption.length + 1)
      }
      (groups, last)

  def propLastOccurrence: Property =
    for (groups, last) <- genOptions.forAll
    yield CompilerArguments.explicitBootClasspath(groups.flatten) ==== last

  def propKeepsUserBoot: Property =
    for
      boot <- genBootOption.forAll
      others <- Gen.list(genOtherOption, Range.linear(0, 4)).forAll
      at <- Gen.int(Range.linear(0, others.size)).forAll
    yield
      val options = others.patch(at, List(boot), 0).flatten
      val log = new RecordingLogger
      val args = makeArguments(autoBootOptions, options, log)
      Result.all(
        List(
          args.take(options.size).toList ==== options,
          args.lift(options.size) ==== Some("-classpath"),
          log.warnings.size ==== 1,
        )
      )
end CompilerArgumentsSpec
