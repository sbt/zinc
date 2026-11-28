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
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarOutputStream
import java.util.zip.{ ZipEntry, ZipFile }
import sbt.util.Logger
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Using
import xsbti.VirtualFile
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
    example("announce no early output while a pickle of an earlier compile is lost", nextCompile),
    example("announce the early output once the source of a lost pickle is gone", sourceGone),
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

  /** The early jar, which holds `J.tasty` already, and the early analysis that compiles share. */
  private final class Early(dir: Path):
    val jar: Path = dir.resolve("early/early.jar")
    Files.createDirectories(jar.getParent)
    earlyJarWithJ(jar)

    val classes: Path = dir.resolve("classes")
    val converter = MappedFileConverter.empty
    private val contents = AtomicReference(Option.empty[AnalysisContents])

    val store: XAnalysisStore = new XAnalysisStore:
      override def get() = contents.get.toJava
      override def unsafeGet() = contents.get.get
      override def set(written: AnalysisContents) = contents.set(Some(written))

    def analysis: Option[AnalysisContents] = contents.get

    def source(name: String): VirtualFile = converter.toVirtualFile(dir.resolve(s"$name.scala"))

    /** The analysis in which the source of each name produces its class, whose pickle is kept. */
    def produces(names: String*): Analysis =
      names.foldLeft(Analysis.empty) { (analysis, name) =>
        val classFile = converter.toVirtualFile(classes.resolve(s"$name.class"))
        analysis.addSource(
          source(name),
          apis = Nil,
          stamp = EmptyStamp,
          info = SourceInfos.emptyInfo,
          nonLocalProducts = List(NonLocalProduct(name, name, classFile, EmptyStamp)),
          localProducts = Nil,
          internalDeps = Nil,
          externalDeps = Nil,
          libraryDeps = Nil
        )
      }
  end Early

  /** One compile with early output into the early jar. */
  private final class Compile(val early: Early):
    val earlyJar: Path = early.jar
    val pickleUpdates = PickleUpdates(Some(earlyJar), Some(early.store))
    private val announcements = AtomicReference(List.empty[Boolean])
    private val writers = AtomicReference(List.empty[JarOutputStream])
    private val output = CompileOutput(early.classes)

    private val progress = new CompileProgress:
      override def afterEarlyOutput(success: Boolean) =
        announcements.updateAndGet(_ :+ success)
        ()

    private val builder = AnalysisCallback.Builder(
      (_, _) => None,
      Stamps.timeWrapBinaryStamps(early.converter),
      IncOptions.of(),
      MiniSetup.of(
        output,
        MiniOptions.of(Array(), Array(), Array()),
        "3.9.0",
        CompileOrder.Mixed,
        true,
        Array()
      ),
      early.converter,
      EarlyLookup,
      output,
      JarUtils.createOutputJarContent(output),
      Some(CompileOutput(earlyJar)),
      Some(early.store),
      pickleUpdates,
      noKeep,
      Some(progress),
      Logger.Null
    )

    def announced: List[Boolean] = announcements.get

    /** Runs a cycle whose compiler is `scalac`, and gives the jar it had for its pickles. */
    def cycle(continue: Boolean, merged: Analysis)(scalac: AnalysisCallback => Unit): Path =
      val callback = builder.build(handler(continue, merged))
      val updatesJar = callback.getPickleJarPair.toScala.get.get2
      scalac(callback)
      callback.getCycleResultOnce
      updatesJar

    /** Scala 3 that started compiling the sources of `names`. */
    def compiling(callback: AnalysisCallback, names: String*): Unit =
      started(callback)
      names.foreach(name => callback.startSource(early.source(name)))

    def started(callback: AnalysisCallback): Unit = callback.phaseListener.phaseStarted("typer")

    /** Writes `entries` into the jar of the cycle's pickles, as Scala 3 does, without an index. */
    def writing(callback: AnalysisCallback, entries: (String, String)*): Array[Byte] =
      val updatesJar = callback.getPickleJarPair.toScala.get.get2
      val out = JarOutputStream(Files.newOutputStream(updatesJar))
      writers.updateAndGet(out :: _)
      write(out, entries)
      Files.readAllBytes(updatesJar)

    /** Scala 3 that wrote `entries` and then called `dependencyPhaseCompleted`. */
    def wrote(callback: AnalysisCallback, entries: (String, String)*): Unit =
      started(callback)
      jar(callback.getPickleJarPair.toScala.get.get2, entries*)
      callback.dependencyPhaseCompleted()

    /** Lets Scala 3 finish writing, and ends the compile. */
    def end(): Unit =
      writers.getAndSet(Nil).foreach(_.close())
      pickleUpdates.delete()

    /** Whether the cycles after the one that had `updatesJar` get a jar of their own. */
    def newJarAfter(updatesJar: Path): Result =
      Result.diff(pickleUpdates.jarPair.map(_._2), Some(updatesJar))(_ != _)

    def earlyAnalysisEmpty: Result =
      early.analysis.map(_.getAnalysis) ==== Some(Analysis.empty)

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
    val compile = Compile(Early(dir))
    try f(compile)
    finally compile.end()
  }

  /** A compile of one cycle, whose compiler is `scalac`: what it announced. */
  private def compile(early: Early, merged: Analysis)(
      scalac: (Compile, AnalysisCallback) => Unit
  ): List[Boolean] =
    val c = Compile(early)
    try c.cycle(continue = false, merged)(scalac(c, _))
    finally c.end()
    c.announced

  private def stillWriting: Result = withCompile { c =>
    val unfinished = AtomicReference(Array.emptyByteArray)
    val updatesJar = c.cycle(continue = false, Analysis.empty) { callback =>
      c.started(callback)
      unfinished.set(c.writing(callback, "J.tasty" -> "j2"))
    }
    val result = Result.all(
      List(
        c.announced ==== List(false),
        c.earlyAnalysisEmpty,
        contents(c.earlyJar) ==== List("J.tasty" -> "j1"),
        Files.readAllBytes(updatesJar).toList ==== unfinished.get.toList,
        c.newJarAfter(updatesJar),
      )
    )
    c.end()
    result.and(c.left ==== Set("early.jar"))
  }

  private def yetToWrite: Result = withCompile { c =>
    val updatesJar = c.cycle(continue = false, Analysis.empty)(c.started)
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
    val first = c.cycle(continue = true, Analysis.empty) { callback =>
      c.started(callback)
      c.writing(callback, "J.tasty" -> "j2")
    }
    val second = c.cycle(continue = false, Analysis.empty)(c.wrote(_, "K.tasty" -> "k"))
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
    val updatesJar =
      c.cycle(continue = false, c.early.produces("J"))(c.writing(_, "J.tasty" -> "j2"))
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
    val updatesJar = c.cycle(continue = false, c.early.produces("J"))(c.wrote(_, "J.tasty" -> "j2"))
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

  private def lostJ(early: Early): List[Boolean] =
    compile(early, early.produces("J", "K")) { (c, callback) =>
      c.compiling(callback, "J")
      c.writing(callback, "J.tasty" -> "j2")
    }

  private def nextCompile: Result = withTempDir { dir =>
    val early = Early(dir)
    val both = early.produces("J", "K")
    val lost = lostJ(early)
    val other = compile(early, both) { (c, callback) =>
      c.compiling(callback, "K")
      c.wrote(callback, "K.tasty" -> "k")
    }
    val stale = contents(early.jar)
    val again = compile(early, both) { (c, callback) =>
      c.compiling(callback, "J")
      c.wrote(callback, "J.tasty" -> "j3")
    }
    Result.all(
      List(
        lost ==== List(false),
        stale ==== List("J.tasty" -> "j1", "K.tasty" -> "k"),
        other ==== List(false),
        again ==== List(true),
        contents(early.jar) ==== List("J.tasty" -> "j3", "K.tasty" -> "k"),
      )
    )
  }

  private def sourceGone: Result = withTempDir { dir =>
    val early = Early(dir)
    val lost = lostJ(early)
    val gone = compile(early, early.produces("K")) { (c, callback) =>
      c.compiling(callback, "K")
      c.wrote(callback, "K.tasty" -> "k")
    }
    Result.all(
      List(
        lost ==== List(false),
        gone ==== List(true),
        contents(early.jar) ==== List("K.tasty" -> "k"),
      )
    )
  }
end PickleUpdatesSpec
