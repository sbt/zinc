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

import java.io.BufferedOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }
import java.nio.file.attribute.FileTime
import java.util.Collections
import java.util.jar.{ Attributes, JarOutputStream, Manifest }
import java.util.zip.{ ZipEntry, ZipFile, ZipOutputStream }

import sbt.io.IO.withTemporaryDirectory as withTmpDir
import sbt.util.Logger
import scala.jdk.CollectionConverters.*
import scala.util.Using

class PickleJarSpec extends UnitSpec:
  private val noKeep: (Path, Long) => Boolean = (_, _) => false
  private val later = 1_800_000_000_000L

  private def zip(path: Path, time: Long, entries: (String, String)*): Path =
    fill(ZipOutputStream(BufferedOutputStream(Files.newOutputStream(path))), time, entries)
    path

  /** A jar like the ones scalac writes pickles into, which start with a manifest. */
  private def pickleJar(path: Path, time: Long, entries: (String, String)*): Path =
    val manifest = Manifest()
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    val out = BufferedOutputStream(Files.newOutputStream(path))
    fill(JarOutputStream(out, manifest), time, entries)
    path

  private def fill(out: ZipOutputStream, time: Long, entries: Seq[(String, String)]): Unit =
    Using.resource(out) { out =>
      entries.foreach { (name, content) =>
        val entry = ZipEntry(name)
        entry.setTime(time)
        out.putNextEntry(entry)
        out.write(content.getBytes(UTF_8))
        out.closeEntry()
      }
    }

  private def contents(jar: Path): List[(String, String)] =
    Using.resource(ZipFile(jar.toFile)) { z =>
      z.entries.asScala.map { e =>
        e.getName -> String(z.getInputStream(e).readAllBytes, UTF_8)
      }.toList
    }

  private def write(jar: Path, updates: Path, known: String*): Unit =
    val products = Option.when(known.nonEmpty)(known.toSet.asJava)
    PickleJar.write(jar, Some(updates), products, noKeep, Logger.Null)

  "PickleJar.write" should "give the same bytes for the same entries" in withTmpDir { tmp =>
    val dir = tmp.toPath
    val ba = Seq("p/B.tasty" -> "b", "p/A.tasty" -> "a")
    write(dir.resolve("a.jar"), zip(dir.resolve("u1.jar"), 0L, ba*))
    write(dir.resolve("b.jar"), zip(dir.resolve("u2.jar"), later, ba.reverse*))
    Files.readAllBytes(dir.resolve("a.jar")) shouldBe Files.readAllBytes(dir.resolve("b.jar"))
    contents(dir.resolve("a.jar")) shouldBe List("p/A.tasty" -> "a", "p/B.tasty" -> "b")
  }

  it should "give the bytes of one write after merges and drops" in withTmpDir { tmp =>
    val dir = tmp.toPath
    val jar = dir.resolve("early.jar")
    write(jar, zip(dir.resolve("u1.jar"), 0L, "p/A.tasty" -> "a", "p/B.tasty" -> "b"))
    write(jar, zip(dir.resolve("u2.jar"), 0L, "p/C.tasty" -> "c", "p/A.tasty" -> "a2"))
    write(jar, zip(dir.resolve("u3.jar"), 0L, "p/A.tasty" -> "a"), "p/A.class", "p/C.class")
    val direct = dir.resolve("direct.jar")
    write(direct, zip(dir.resolve("u4.jar"), 0L, "p/C.tasty" -> "c", "p/A.tasty" -> "a"))
    Files.readAllBytes(jar) shouldBe Files.readAllBytes(direct)
    dir.toFile.list.toSet shouldBe Set("early.jar", "direct.jar")
  }

  it should "not write to a jar that holds its entries already" in withTmpDir { tmp =>
    val dir = tmp.toPath
    val jar = dir.resolve("early.jar")
    val ab = Seq("p/A.tasty" -> "a", "p/B.tasty" -> "b")
    write(jar, pickleJar(dir.resolve("u1.jar"), 0L, ab*))
    val before = Files.readAllBytes(jar)
    jar.toFile.setWritable(false)
    try
      PickleJar.write(jar, None, None, noKeep, Logger.Null)
      write(jar, pickleJar(dir.resolve("u2.jar"), later, ab.reverse*))
    finally jar.toFile.setWritable(true)
    Files.readAllBytes(jar) shouldBe before
  }

  it should "rewrite only from the first entry whose data changes" in withTmpDir { tmp =>
    val dir = tmp.toPath
    val jar = dir.resolve("early.jar")
    val abc = Seq("p/A.tasty" -> "a", "p/B.tasty" -> "b", "p/C.tasty" -> "c")
    write(jar, pickleJar(dir.resolve("u1.jar"), 0L, abc*))
    val before = Files.readAllBytes(jar)
    val offsetOfC = SortedJar.entries(jar).find(_.name == "p/C.tasty").get.offset
    var copied = Option.empty[(Array[Byte], Long)]
    val keep: (Path, Long) => Boolean = (copy, from) =>
      copied = Some((Files.readAllBytes(copy), from))
      false
    val updates = pickleJar(dir.resolve("u2.jar"), later, "p/A.tasty" -> "a", "p/C.tasty" -> "c2")
    PickleJar.write(jar, Some(updates), None, keep, Logger.Null)
    val (copy, from) = copied.get
    from shouldBe offsetOfC
    copy shouldBe before.drop(from.toInt)
    Files.readAllBytes(jar).take(from.toInt) shouldBe before.take(from.toInt)
    contents(jar) shouldBe List("p/A.tasty" -> "a", "p/B.tasty" -> "b", "p/C.tasty" -> "c2")
  }

  it should "drop the manifest, and every pickle once no class is known" in withTmpDir { tmp =>
    val dir = tmp.toPath
    val jar = dir.resolve("early.jar")
    write(jar, pickleJar(dir.resolve("u.jar"), 0L, "p/A.tasty" -> "a"))
    PickleJar.write(jar, None, None, noKeep, Logger.Null)
    contents(jar) shouldBe List("p/A.tasty" -> "a")
    PickleJar.write(jar, None, Some(Collections.emptySet), noKeep, Logger.Null)
    val empty = dir.resolve("empty.jar")
    PickleJar.touch(empty)
    Files.readAllBytes(jar) shouldBe Files.readAllBytes(empty)
  }

  it should "rewrite a jar that an earlier zinc wrote as one write of its entries" in withTmpDir {
    tmp =>
      val dir = tmp.toPath
      val jar = zip(dir.resolve("early.jar"), later, "p/C.tasty" -> "c", "p/A.tasty" -> "a")
      IndexBasedZipFsOps.mergeArchives(jar, zip(dir.resolve("u1.jar"), later, "p/A.tasty" -> "a2"))
      val updates = zip(dir.resolve("u2.jar"), 0L, "p/D.tasty" -> "d", "p/A.tasty" -> "a2")
      write(jar, updates, "p/A.class", "p/D.class")
      val fresh = dir.resolve("fresh.jar")
      write(fresh, zip(dir.resolve("u3.jar"), 0L, "p/A.tasty" -> "a2", "p/D.tasty" -> "d"))
      Files.readAllBytes(jar) shouldBe Files.readAllBytes(fresh)
  }

  it should "leave a later mtime than the jar had, which a coarse clock may not" in withTmpDir {
    tmp =>
      val dir = tmp.toPath
      val jar = dir.resolve("early.jar")
      write(jar, zip(dir.resolve("u1.jar"), 0L, "p/A.tasty" -> "a"))
      val ahead = FileTime.fromMillis(System.currentTimeMillis + 3_600_000L)
      Files.setLastModifiedTime(jar, ahead)
      write(jar, zip(dir.resolve("u2.jar"), 0L, "p/A.tasty" -> "a2"))
      Files.getLastModifiedTime(jar).compareTo(ahead) should be > 0
  }

  it should "write a zip64 end for more than 65535 entries" in withTmpDir { tmp =>
    val dir = tmp.toPath
    val jar = dir.resolve("early.jar")
    val entries = (0 until 70000).map(i => f"p/C$i%05d.tasty" -> i.toString)
    write(jar, zip(dir.resolve("u.jar"), 0L, entries*))
    Using.resource(ZipFile(jar.toFile))(_.size) shouldBe 70000
    SortedJar.entries(jar).size shouldBe 70000
  }
end PickleJarSpec
