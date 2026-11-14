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
import java.nio.file.{ Files, Path }
import java.nio.file.attribute.FileTime

import sbt.internal.inc.HedgehogSupport.withTempDir

object ClassFinderSpec extends Properties:
  override def tests: List[Test] = List(
    example("report new and rewritten class files, and skip unchanged ones", newAndRewritten),
    example("report a rewrite by its size when its time did not change", sameTime),
    example("treat a class file whose stamp cannot be read as changed", unreadable),
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
        before.keySet ==== Set(kept, rewritten, deleted),
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
        stamps.keySet ==== Set(present),
        classes.changedSince(Map.empty) ==== Seq(present, missing),
        classes.changedSince(stamps) ==== Seq(missing),
      )
    )
end ClassFinderSpec
