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
import java.nio.file.{ FileSystems, Files, Path }
import java.nio.file.attribute.FileTime
import java.util.Collections

import sbt.internal.inc.HedgehogSupport.withTempDir

object ClassFinderSpec extends Properties:
  override def tests: List[Test] = List(
    example(
      "DirectoryClassFinder reports new and rewritten class files since a stamp snapshot",
      newAndRewritten
    ),
    example("DirectoryClassFinder reports every class file against an empty snapshot", allNew),
    example(
      "DirectoryClassFinder reports a rewritten class file by size when its time did not change",
      sameTime
    ),
    example("Classes treat a path whose attributes cannot be read as changed", unreadable),
    example("JarClassFinder compares entries across separately opened jar filesystems", jarEntries),
    example("JarClassFinder has no stamps for a missing jar", missingJar),
  )

  private def write(p: Path, content: String): Path =
    Files.createDirectories(p.getParent)
    Files.write(p, content.getBytes("UTF-8"))

  /** Files that pre-exist a compile are old; do not depend on timestamp resolution. */
  private def backdate(p: Path): Unit =
    Files.setLastModifiedTime(p, FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis - 10000))

  private def newAndRewritten: Result = withTempDir: base =>
    val kept = write(base.resolve("p/Kept.class"), "kept")
    val rewritten = write(base.resolve("p/Rewritten.class"), "old")
    val deleted = write(base.resolve("p/Deleted.class"), "deleted")
    Seq(kept, rewritten, deleted).foreach(backdate)
    val finder = new DirectoryClassFinder(base)

    val before = finder.classes.stampsAndClose()

    write(rewritten, "new") // same size, new time
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

  private def allNew: Result = withTempDir: base =>
    val a = write(base.resolve("A.class"), "a")
    val b = write(base.resolve("q/B.class"), "b")
    write(base.resolve("q/notes.txt"), "not a class file")
    val classes = new DirectoryClassFinder(base).classes
    try classes.changedSince(Map.empty).toSet ==== Set(a, b)
    finally classes.close()

  private def sameTime: Result = withTempDir: base =>
    val same = write(base.resolve("Same.class"), "one")
    val time = FileTime.fromMillis(Files.getLastModifiedTime(same).toMillis - 10000)
    Files.setLastModifiedTime(same, time)
    val finder = new DirectoryClassFinder(base)
    val before = finder.classes.stampsAndClose()

    write(same, "longer content")
    Files.setLastModifiedTime(same, time) // a coarse filesystem can hand out the same time

    val after = finder.classes
    try after.changedSince(before) ==== Seq(same)
    finally after.close()

  private def unreadable: Result = withTempDir: dir =>
    val present = write(dir.resolve("Present.class"), "p")
    val missing = dir.resolve("Missing.class")
    val classes = new Classes:
      def paths = Seq(present, missing)
    Result.all(
      List(
        ClassStamp.of(missing) ==== None,
        classes.stamps.keySet ==== Set(present.toString),
        classes.changedSince(Map.empty) ==== Seq(present, missing),
        classes.changedSince(classes.stamps) ==== Seq(missing),
      )
    )

  private def jarEntries: Result = withTempDir: dir =>
    val jar = dir.resolve("out.jar")
    val create = FileSystems.newFileSystem(
      URI.create("jar:" + jar.toUri),
      Collections.singletonMap("create", "true")
    )
    try write(create.getPath("/p/A.class"), "a")
    finally create.close()
    val finder = new JarClassFinder(jar)

    val before = finder.classes.stampsAndClose()

    val unchanged = finder.classes
    val unchangedSince =
      try unchanged.changedSince(before).map(_.toString)
      finally unchanged.close()

    val update = FileSystems.newFileSystem(jar, null.asInstanceOf[ClassLoader])
    try write(update.getPath("/p/B.class"), "b")
    finally update.close()

    val after = finder.classes
    val afterSince =
      try after.changedSince(before).map(_.toString)
      finally after.close()
    Result.all(
      List(
        before.keySet ==== Set("/p/A.class"),
        unchangedSince ==== Seq.empty,
        afterSince ==== Seq("/p/B.class"),
      )
    )

  private def missingJar: Result = withTempDir: dir =>
    new JarClassFinder(dir.resolve("missing.jar")).classes.stampsAndClose() ==== Map.empty
end ClassFinderSpec
