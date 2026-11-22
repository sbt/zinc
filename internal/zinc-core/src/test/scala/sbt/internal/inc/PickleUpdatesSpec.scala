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

package sbt.internal.inc

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }
import java.util.jar.JarOutputStream
import java.util.zip.{ ZipEntry, ZipFile }
import sbt.util.Logger
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Using
import xsbti.VirtualFileRef
import xsbti.compile.{
  AnalysisContents,
  AnalysisStore as XAnalysisStore,
  CompileOrder,
  CompileProgress,
  IncOptions,
  MiniOptions,
  MiniSetup
}
import Analysis.NonLocalProduct
import HedgehogSupport.withTempDir
import Incremental.CompileCycleResult

/**
 * Scala 3 writes the pickles of a cycle on a background thread, which it does not wait for when it
 * skips `dependencyPhaseCompleted` (scala/scala3#27139): the jar it writes them into then has no
 * index yet. Here the compiler of each cycle is played by a function of the callback.
 */
object PickleUpdatesSpec extends Properties:
  override def tests: List[Test] = List(
    example("leave alone the pickles that Scala 3 is still writing", stillWriting),
    example("leave alone the jar that Scala 3 has yet to write when its run ends", yetToWrite),
    example("announce no early output once a cycle has lost its pickles", laterCycle),
    example("leave alone pickles without an index when scalac reports no phase", noIndex),
    example("announce the early output when Scala 3 wrote the pickles", wrote),
    example("merge no pickles from a jar without an index", writeIfComplete),
  )

  private val noKeep: (Path, Long) => Boolean = (_, _) => false

  private def jar(path: Path, entries: (String, String)*): Path =
    Using.resource(JarOutputStream(Files.newOutputStream(path)))(write(_, entries))
    path

  private def write(out: JarOutputStream, entries: Seq[(String, String)]): Unit =
    entries.foreach { (name, content) =>
      out.putNextEntry(ZipEntry(name))
      out.write(content.getBytes(UTF_8))
      out.flush()
    }

  private def contents(jar: Path): List[(String, String)] =
    Using.resource(ZipFile(jar.toFile)) { z =>
      def content(e: ZipEntry) = String(z.getInputStream(e).readAllBytes, UTF_8)
      z.entries.asScala.map(e => e.getName -> content(e)).toList
    }

  /** Writes an early jar that holds `J.tasty` from an earlier compile. */
  private def earlyJarWithJ(earlyJar: Path): Unit =
    val updates = jar(earlyJar.resolveSibling("j1.jar"), "J.tasty" -> "j1")
    PickleJar.write(earlyJar, Some(updates), None, noKeep, Logger.Null)

  private object EarlyLookup extends Lookup with NoopExternalLookup:
    override def changedClasspathHash = None
    override def analyses = Vector.empty
    override def lookupOnClasspath(binaryClassName: String) = None
    override def lookupAnalysis(binaryClassName: String) = None

  /** One compile with early output into an early jar that holds `J.tasty` already. */
  private final class Compile(dir: Path):
    val earlyJar: Path = dir.resolve("early/early.jar")
    Files.createDirectories(earlyJar.getParent)
    earlyJarWithJ(earlyJar)

    val pickleUpdates = PickleUpdates(Some(earlyJar))
    var announced = List.empty[Boolean]
    var earlyAnalysis = Option.empty[AnalysisContents]
    private var writers = List.empty[JarOutputStream]
    private val classes = dir.resolve("classes")
    private val output = CompileOutput(classes)
    private val converter = MappedFileConverter.empty

    private val store = new XAnalysisStore:
      override def get() = earlyAnalysis.toJava
      override def unsafeGet() = earlyAnalysis.get
      override def set(contents: AnalysisContents) = earlyAnalysis = Some(contents)

    private val progress = new CompileProgress:
      override def afterEarlyOutput(success: Boolean) = announced :+= success

    private val builder = AnalysisCallback.Builder(
      (_, _) => None,
      Stamps.timeWrapBinaryStamps(converter),
      IncOptions.of(),
      MiniSetup.of(
        output,
        MiniOptions.of(Array(), Array(), Array()),
        "3.9.0",
        CompileOrder.Mixed,
        true,
        Array()
      ),
      converter,
      EarlyLookup,
      output,
      JarUtils.createOutputJarContent(output),
      Some(CompileOutput(earlyJar)),
      Some(store),
      pickleUpdates,
      noKeep,
      Some(progress),
      Logger.Null
    )

    /** The analysis in which `J.java` produces `J.class`, so that the early jar keeps J.tasty. */
    val producesJ: Analysis =
      val classFile = converter.toVirtualFile(classes.resolve("J.class"))
      val product = NonLocalProduct("J", "J", classFile, EmptyStamp)
      Analysis.empty.addSource(
        VirtualFileRef.of("J.java"),
        apis = Nil,
        stamp = EmptyStamp,
        info = SourceInfos.emptyInfo,
        nonLocalProducts = List(product),
        localProducts = Nil,
        internalDeps = Nil,
        externalDeps = Nil,
        libraryDeps = Nil
      )

    /** Runs a cycle whose compiler is `scalac`, and gives the jar it had for its pickles. */
    def cycle(continue: Boolean, merged: Analysis = Analysis.empty)(
        scalac: AnalysisCallback => Unit
    ): Path =
      val callback = builder.build(handler(continue, merged))
      val updatesJar = callback.getPickleJarPair.toScala.get.get2
      scalac(callback)
      callback.getCycleResultOnce
      updatesJar

    def started(callback: AnalysisCallback): Unit = callback.phaseListener.phaseStarted("typer")

    /** Writes `entries` into the jar of the cycle's pickles, as Scala 3 does, without an index. */
    def writing(callback: AnalysisCallback, entries: (String, String)*): Array[Byte] =
      val updatesJar = callback.getPickleJarPair.toScala.get.get2
      val out = JarOutputStream(Files.newOutputStream(updatesJar))
      writers ::= out
      write(out, entries)
      Files.readAllBytes(updatesJar)

    /** Scala 3 that wrote `entries` and then called `dependencyPhaseCompleted`. */
    def wrote(callback: AnalysisCallback, entries: (String, String)*): Unit =
      started(callback)
      jar(callback.getPickleJarPair.toScala.get.get2, entries*)
      callback.dependencyPhaseCompleted()

    /** Lets Scala 3 finish writing, and ends the compile. */
    def end(): Unit =
      writers.foreach(_.close())
      writers = Nil
      pickleUpdates.delete()

    /** Whether the cycles after the one that had `updatesJar` get a jar of their own. */
    def newJarAfter(updatesJar: Path): Result =
      Result.diff(pickleUpdates.jarPair.map(_._2), Some(updatesJar))(_ != _)

    def earlyAnalysisEmpty: Result =
      earlyAnalysis.map(_.getAnalysis) ==== Some(Analysis.empty)

    def left: Set[String] = earlyJar.getParent.toFile.list.toSet

    private def handler(continue: Boolean, merged: Analysis) =
      new Incremental.IncrementalCallback(ClassFileManager.deleteImmediately(Array())):
        override def mergeAndInvalidate(partialAnalysis: Analysis, shouldRegisterCycle: Boolean) =
          CompileCycleResult(continue, Set.empty, merged)
        override def completeCycle(
            prev: Option[CompileCycleResult],
            partialAnalysis: Analysis,
            shouldRegisterCycle: Boolean
        ) = CompileCycleResult(continue, Set.empty, merged)
        override def previousAnalysisPruned = merged
        override def previousAnalysis = merged
        override def isFullCompilation = false
  end Compile

  private def withCompile(f: Compile => Result): Result = withTempDir { dir =>
    val compile = Compile(dir)
    try f(compile)
    finally compile.end()
  }

  private def stillWriting: Result = withCompile { c =>
    var unfinished = Array.emptyByteArray
    val updatesJar = c.cycle(continue = false) { callback =>
      c.started(callback)
      unfinished = c.writing(callback, "J.tasty" -> "j2")
    }
    val result = Result.all(
      List(
        c.announced ==== List(false),
        c.earlyAnalysisEmpty,
        contents(c.earlyJar) ==== List("J.tasty" -> "j1"),
        Files.readAllBytes(updatesJar).toList ==== unfinished.toList,
        c.newJarAfter(updatesJar),
      )
    )
    c.end()
    result.and(c.left ==== Set("early.jar"))
  }

  private def yetToWrite: Result = withCompile { c =>
    val updatesJar = c.cycle(continue = false)(c.started)
    Result.all(
      List(
        c.announced ==== List(false),
        c.earlyAnalysisEmpty,
        Result.assert(Files.exists(updatesJar)).log("deleted the jar Scala 3 is about to write"),
        c.newJarAfter(updatesJar),
      )
    )
  }

  private def laterCycle: Result = withCompile { c =>
    val first = c.cycle(continue = true) { callback =>
      c.started(callback)
      c.writing(callback, "J.tasty" -> "j2")
    }
    val second = c.cycle(continue = false)(c.wrote(_, "K.tasty" -> "k"))
    Result.all(
      List(
        c.announced ==== List(false),
        c.earlyAnalysisEmpty,
        Result.diff(second, first)(_ != _),
        contents(c.earlyJar) ==== List("J.tasty" -> "j1", "K.tasty" -> "k"),
        Result.assert(Files.exists(first)).log("the jar Scala 3 is still writing was deleted"),
        Result.assert(!Files.exists(second)).log("the pickles of the second cycle were not merged"),
      )
    )
  }

  private def noIndex: Result = withCompile { c =>
    val updatesJar = c.cycle(continue = false, c.producesJ)(c.writing(_, "J.tasty" -> "j2"))
    Result.all(
      List(
        c.announced ==== List(false),
        c.earlyAnalysisEmpty,
        contents(c.earlyJar) ==== List("J.tasty" -> "j1"),
        Result.assert(Files.exists(updatesJar)).log("the jar without an index was deleted"),
        c.newJarAfter(updatesJar),
      )
    )
  }

  private def wrote: Result = withCompile { c =>
    val updatesJar = c.cycle(continue = false, c.producesJ)(c.wrote(_, "J.tasty" -> "j2"))
    Result.all(
      List(
        c.announced ==== List(true),
        contents(c.earlyJar) ==== List("J.tasty" -> "j2"),
        Result.assert(!Files.exists(updatesJar)).log("the pickles were not merged"),
        c.pickleUpdates.jarPair.map(_._2) ==== Some(updatesJar),
      )
    )
  }

  private def writeIfComplete: Result = withTempDir { dir =>
    val earlyJar = dir.resolve("early.jar")
    earlyJarWithJ(earlyJar)
    val before = Files.readAllBytes(earlyJar)
    val updatesJar = dir.resolve("updates.jar")
    Using.resource(JarOutputStream(Files.newOutputStream(updatesJar))) { out =>
      write(out, List("J.tasty" -> "j2"))
      val unfinished = Files.readAllBytes(updatesJar)
      val merged =
        PickleJar.writeIfComplete(earlyJar, Some(updatesJar), None, noKeep, Logger.Null)
      Result.all(
        List(
          Result.assert(!merged).log("merged a jar without an index"),
          Files.readAllBytes(earlyJar).toList ==== before.toList,
          Files.readAllBytes(updatesJar).toList ==== unfinished.toList,
        )
      )
    }
  }
end PickleUpdatesSpec
