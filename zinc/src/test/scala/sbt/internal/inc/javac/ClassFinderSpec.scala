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

package sbt.internal.inc.javac

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.net.URI
import java.nio.file.{ FileSystem, FileSystems, Files, Path }
import java.nio.file.attribute.FileTime
import java.util.Collections

import scala.util.Using

import sbt.internal.inc.HedgehogSupport.withTempDir
import sbt.io.{ IO, PathFinder }

object ClassFinderSpec extends Properties:
  override def tests: List[Test] = List(
    example("report new and rewritten class files, and skip unchanged ones", newAndRewritten),
    example("report a rewrite by its size when its time did not change", sameTime),
    example("treat a class file whose stamp cannot be read as changed", unreadable),
    example("find the class files that PathFinder finds", sameAsPathFinder),
    example("stamp the class files of a directory while walking it", stampedByWalk),
    example("report only new and rewritten entries of a jar that was reopened", jarEntries),
  )

  private def write(p: Path, content: String): Path =
    Files.createDirectories(p.getParent)
    Files.write(p, content.getBytes("UTF-8"))

  private def tenSecondsEarlier(p: Path): FileTime =
    FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis - 10000)

  private def newAndRewritten: Result = withTempDir: base =>
    val kept = write(base.resolve("p/Kept.class"), "kept")
    val rewritten = write(base.resolve("p/Rewritten.class"), "old")
    val deleted = write(base.resolve("p/Deleted.class"), "deleted")
    Seq(kept, rewritten, deleted).foreach(p => Files.setLastModifiedTime(p, tenSecondsEarlier(p)))
    val finder = new DirectoryClassFinder(base)
    val before = finder.classes.stampsAndClose()
    write(rewritten, "new")
    Files.delete(deleted)
    val added = write(base.resolve("p/Added.class"), "added")
    val after = finder.classes
    val changed =
      try after.changedSince(before).toSet
      finally after.close()
    Result.all(
      List(
        before.keySet ==== Set(kept, rewritten, deleted).map(_.toString),
        changed ==== Set(rewritten, added),
      )
    )

  private def sameTime: Result = withTempDir: base =>
    val same = write(base.resolve("Same.class"), "one")
    val time = tenSecondsEarlier(same)
    Files.setLastModifiedTime(same, time)
    val finder = new DirectoryClassFinder(base)
    val before = finder.classes.stampsAndClose()
    write(same, "longer content")
    Files.setLastModifiedTime(same, time)
    val after = finder.classes
    try after.changedSince(before) ==== Seq(same)
    finally after.close()

  private def unreadable: Result = withTempDir: dir =>
    val present = write(dir.resolve("Present.class"), "p")
    val missing = dir.resolve("Missing.class")
    val classes = new Classes:
      def paths = Seq(present, missing)
    val stamps = classes.stampsAndClose()
    Result.all(
      List(
        stamps.keySet ==== Set(present.toString),
        classes.changedSince(Map.empty) ==== Seq(present, missing),
        classes.changedSince(stamps) ==== Seq(missing),
      )
    )

  private def sameAsPathFinder: Result = withTempDir: base =>
    write(base.resolve("A.class"), "a")
    write(base.resolve("p/q/B.class"), "b")
    write(base.resolve("p/q/B.tasty"), "t")
    val elsewhere = Files.createTempDirectory("class-finder-linked")
    try
      write(elsewhere.resolve("C.class"), "c")
      Files.createSymbolicLink(base.resolve("linked"), elsewhere)
      val expected = (PathFinder(base.toFile) ** "*.class").get().map(_.toPath).toSet
      val classes = new DirectoryClassFinder(base).classes
      Result.all(
        List(
          expected.size ==== 3,
          classes.paths.toSet ==== expected,
          classes.stamps.keySet ==== expected.map(_.toString),
          new DirectoryClassFinder(base.resolve("missing")).classes.paths ==== Nil,
        )
      )
    finally IO.delete(elsewhere.toFile)

  private def stampedByWalk: Result = withTempDir: base =>
    val walked = write(base.resolve("p/Walked.class"), "walked")
    val expected = ClassStamp.of(walked)
    val classes = new DirectoryClassFinder(base).classes
    Files.delete(walked)
    classes.stamps.get(walked.toString) ==== expected

  private def updateJar(jar: Path)(f: FileSystem => Unit): Unit =
    val uri = URI.create("jar:" + jar.toUri)
    Using.resource(FileSystems.newFileSystem(uri, Collections.singletonMap("create", "true")))(f)

  private def jarEntries: Result = withTempDir: base =>
    val jar = base.resolve("out.jar")
    val longAgo = FileTime.fromMillis(1000000000000L)
    updateJar(jar): fs =>
      Seq("/p/Kept.class", "/p/Rewritten.class", "/p/Deleted.class").foreach: entry =>
        Files.setLastModifiedTime(write(fs.getPath(entry), "old"), longAgo)
    val finder = new JarClassFinder(jar)
    val before = finder.classes.stampsAndClose()
    updateJar(jar): fs =>
      write(fs.getPath("/p/Rewritten.class"), "new")
      Files.delete(fs.getPath("/p/Deleted.class"))
      write(fs.getPath("/p/Added.class"), "added")
    val after = finder.classes
    val changed =
      try after.changedSince(before).map(_.toString).toSet
      finally after.close()
    Result.all(
      List(
        before.size ==== 3,
        changed ==== Set("/p/Rewritten.class", "/p/Added.class"),
      )
    )
end ClassFinderSpec
