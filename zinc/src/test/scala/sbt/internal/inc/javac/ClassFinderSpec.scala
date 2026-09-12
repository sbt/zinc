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

import java.net.URI
import java.nio.file.{ FileSystems, Files, Path }
import java.nio.file.attribute.FileTime
import java.util.Collections

import sbt.internal.inc.UnitSpec
import sbt.io.IO

class ClassFinderSpec extends UnitSpec {
  private def write(p: Path, content: String): Path = {
    Files.createDirectories(p.getParent)
    Files.write(p, content.getBytes("UTF-8"))
  }

  /** Files that pre-exist a compile are old; do not depend on timestamp resolution. */
  private def backdate(p: Path): Unit =
    Files.setLastModifiedTime(p, FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis - 10000))

  "DirectoryClassFinder" should "report new and rewritten class files since a stamp snapshot" in {
    IO.withTemporaryDirectory { dir =>
      val base = dir.toPath
      val kept = write(base.resolve("p/Kept.class"), "kept")
      val rewritten = write(base.resolve("p/Rewritten.class"), "old")
      val deleted = write(base.resolve("p/Deleted.class"), "deleted")
      Seq(kept, rewritten, deleted).foreach(backdate)
      val finder = new DirectoryClassFinder(base)

      val before = finder.classes.stampsAndClose()
      before.keySet shouldBe Set(kept, rewritten, deleted).map(_.toString)

      write(rewritten, "new") // same size, new time
      Files.delete(deleted)
      val added = write(base.resolve("p/Added.class"), "added")

      val after = finder.classes
      try after.changedSince(before).toSet shouldBe Set(rewritten, added)
      finally after.close()
    }
  }

  it should "report every class file against an empty snapshot" in {
    IO.withTemporaryDirectory { dir =>
      val base = dir.toPath
      val a = write(base.resolve("A.class"), "a")
      val b = write(base.resolve("q/B.class"), "b")
      write(base.resolve("q/notes.txt"), "not a class file")
      val classes = new DirectoryClassFinder(base).classes
      try classes.changedSince(Map.empty).toSet shouldBe Set(a, b)
      finally classes.close()
    }
  }

  it should "report a rewritten class file by size when its time did not change" in {
    IO.withTemporaryDirectory { dir =>
      val base = dir.toPath
      val same = write(base.resolve("Same.class"), "one")
      val time = FileTime.fromMillis(Files.getLastModifiedTime(same).toMillis - 10000)
      Files.setLastModifiedTime(same, time)
      val finder = new DirectoryClassFinder(base)
      val before = finder.classes.stampsAndClose()

      write(same, "longer content")
      Files.setLastModifiedTime(same, time) // a coarse filesystem can hand out the same time

      val after = finder.classes
      try after.changedSince(before) shouldBe Seq(same)
      finally after.close()
    }
  }

  "Classes" should "treat a path whose attributes cannot be read as changed" in {
    IO.withTemporaryDirectory { dir =>
      val present = write(dir.toPath.resolve("Present.class"), "p")
      val missing = dir.toPath.resolve("Missing.class")
      val classes = new Classes { def paths = Seq(present, missing) }
      ClassStamp.of(missing) shouldBe None
      classes.stamps.keySet shouldBe Set(present.toString)
      classes.changedSince(Map.empty) shouldBe Seq(present, missing)
      classes.changedSince(classes.stamps) shouldBe Seq(missing)
    }
  }

  "JarClassFinder" should "compare entries across separately opened jar filesystems" in {
    IO.withTemporaryDirectory { dir =>
      val jar = dir.toPath.resolve("out.jar")
      val create = FileSystems.newFileSystem(
        URI.create("jar:" + jar.toUri),
        Collections.singletonMap("create", "true")
      )
      try write(create.getPath("/p/A.class"), "a")
      finally create.close()
      val finder = new JarClassFinder(jar)

      val before = finder.classes.stampsAndClose()
      before.keySet shouldBe Set("/p/A.class")

      val unchanged = finder.classes
      try unchanged.changedSince(before) shouldBe empty
      finally unchanged.close()

      val update = FileSystems.newFileSystem(jar, null.asInstanceOf[ClassLoader])
      try write(update.getPath("/p/B.class"), "b")
      finally update.close()

      val after = finder.classes
      try after.changedSince(before).map(_.toString) shouldBe Seq("/p/B.class")
      finally after.close()
    }
  }

  it should "have no stamps for a missing jar" in {
    IO.withTemporaryDirectory { dir =>
      new JarClassFinder(dir.toPath.resolve("missing.jar")).classes.stampsAndClose() shouldBe empty
    }
  }
}
