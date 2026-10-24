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
import java.io.BufferedOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }
import java.nio.file.attribute.FileTime
import java.util.Collections
import java.util.jar.{ Attributes, JarOutputStream, Manifest }
import java.util.zip.{ ZipEntry, ZipFile, ZipOutputStream }
import sbt.util.Logger
import scala.jdk.CollectionConverters.*
import scala.util.Using
import HedgehogSupport.{ shuffle, withTempDir }

object PickleJarSpec extends Properties:
  override def tests: List[Test] = List(
    example("PickleJar.write should give the same bytes for the same entries", sameEntries),
    example("give the bytes of one write after merges and drops", mergesAndDrops),
    example("not write to a jar that holds its entries already", noRewrite),
    example("rewrite only from the first entry whose data changes", rewriteFromChange),
    example("drop the manifest, and every pickle once no class is known", dropAll),
    example("rewrite a jar that an earlier zinc wrote as one write of its entries", earlierZinc),
    example("leave a later mtime than the jar had, which a coarse clock may not", laterMtime),
    example("write a zip64 end for more than 65535 entries", zip64),
    property("give the same bytes for any order and time of the same entries", propSameBytes)
      .withTests(30),
    property("give the bytes of one write after any sequence of merges", propMerges)
      .withTests(30),
    property("keep exactly the pickles of the known classes", propKnownProducts)
      .withTests(30),
  )

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

  private def sameBytes(a: Path, b: Path): Result =
    Files.readAllBytes(a).toList ==== Files.readAllBytes(b).toList

  private def sameEntries: Result = withTempDir { dir =>
    val ba = Seq("p/B.tasty" -> "b", "p/A.tasty" -> "a")
    write(dir.resolve("a.jar"), zip(dir.resolve("u1.jar"), 0L, ba*))
    write(dir.resolve("b.jar"), zip(dir.resolve("u2.jar"), later, ba.reverse*))
    Result.all(
      List(
        sameBytes(dir.resolve("a.jar"), dir.resolve("b.jar")),
        contents(dir.resolve("a.jar")) ==== List("p/A.tasty" -> "a", "p/B.tasty" -> "b"),
      )
    )
  }

  private def mergesAndDrops: Result = withTempDir { dir =>
    val jar = dir.resolve("early.jar")
    write(jar, zip(dir.resolve("u1.jar"), 0L, "p/A.tasty" -> "a", "p/B.tasty" -> "b"))
    write(jar, zip(dir.resolve("u2.jar"), 0L, "p/C.tasty" -> "c", "p/A.tasty" -> "a2"))
    write(jar, zip(dir.resolve("u3.jar"), 0L, "p/A.tasty" -> "a"), "p/A.class", "p/C.class")
    val direct = dir.resolve("direct.jar")
    write(direct, zip(dir.resolve("u4.jar"), 0L, "p/C.tasty" -> "c", "p/A.tasty" -> "a"))
    Result.all(
      List(
        sameBytes(jar, direct),
        dir.toFile.list.toSet ==== Set("early.jar", "direct.jar"),
      )
    )
  }

  private def noRewrite: Result = withTempDir { dir =>
    val jar = dir.resolve("early.jar")
    val ab = Seq("p/A.tasty" -> "a", "p/B.tasty" -> "b")
    write(jar, pickleJar(dir.resolve("u1.jar"), 0L, ab*))
    val before = Files.readAllBytes(jar)
    jar.toFile.setWritable(false)
    try
      PickleJar.write(jar, None, None, noKeep, Logger.Null)
      write(jar, pickleJar(dir.resolve("u2.jar"), later, ab.reverse*))
    finally jar.toFile.setWritable(true)
    Files.readAllBytes(jar).toList ==== before.toList
  }

  private def rewriteFromChange: Result = withTempDir { dir =>
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
    copied match
      case Some((copy, from)) =>
        Result.all(
          List(
            from ==== offsetOfC,
            copy.toList ==== before.drop(from.toInt).toList,
            Files.readAllBytes(jar).take(from.toInt).toList ==== before.take(from.toInt).toList,
            contents(jar) ==== List("p/A.tasty" -> "a", "p/B.tasty" -> "b", "p/C.tasty" -> "c2"),
          )
        )
      case None => Result.failure.log("keep was not called")
  }

  private def dropAll: Result = withTempDir { dir =>
    val jar = dir.resolve("early.jar")
    write(jar, pickleJar(dir.resolve("u.jar"), 0L, "p/A.tasty" -> "a"))
    PickleJar.write(jar, None, None, noKeep, Logger.Null)
    val withoutManifest = contents(jar)
    PickleJar.write(jar, None, Some(Collections.emptySet), noKeep, Logger.Null)
    val empty = dir.resolve("empty.jar")
    PickleJar.touch(empty)
    Result.all(List(withoutManifest ==== List("p/A.tasty" -> "a"), sameBytes(jar, empty)))
  }

  private def earlierZinc: Result = withTempDir { dir =>
    val jar = zip(dir.resolve("early.jar"), later, "p/C.tasty" -> "c", "p/A.tasty" -> "a")
    IndexBasedZipFsOps.mergeArchives(jar, zip(dir.resolve("u1.jar"), later, "p/A.tasty" -> "a2"))
    val updates = zip(dir.resolve("u2.jar"), 0L, "p/D.tasty" -> "d", "p/A.tasty" -> "a2")
    write(jar, updates, "p/A.class", "p/D.class")
    val fresh = dir.resolve("fresh.jar")
    write(fresh, zip(dir.resolve("u3.jar"), 0L, "p/A.tasty" -> "a2", "p/D.tasty" -> "d"))
    sameBytes(jar, fresh)
  }

  private def laterMtime: Result = withTempDir { dir =>
    val jar = dir.resolve("early.jar")
    write(jar, zip(dir.resolve("u1.jar"), 0L, "p/A.tasty" -> "a"))
    val ahead = FileTime.fromMillis(System.currentTimeMillis + 3_600_000L)
    Files.setLastModifiedTime(jar, ahead)
    write(jar, zip(dir.resolve("u2.jar"), 0L, "p/A.tasty" -> "a2"))
    Result.diff(Files.getLastModifiedTime(jar), ahead)(_.compareTo(_) > 0)
  }

  private def zip64: Result = withTempDir { dir =>
    val jar = dir.resolve("early.jar")
    val entries = (0 until 70000).map(i => f"p/C$i%05d.tasty" -> i.toString)
    write(jar, zip(dir.resolve("u.jar"), 0L, entries*))
    Result.all(
      List(
        Using.resource(ZipFile(jar.toFile))(_.size) ==== 70000,
        SortedJar.entries(jar).size ==== 70000,
      )
    )
  }

  private val genClassName: Gen[String] =
    for
      head <- Gen.upper
      tail <- Gen.string(Gen.alphaNum, Range.linear(0, 8))
    yield s"$head$tail"

  private val genPickles: Gen[Map[String, String]] =
    Gen
      .list(
        for
          name <- genClassName
          ext <- Gen.element1(".tasty", ".sig")
          content <- Gen.string(Gen.alphaNum, Range.linear(0, 16))
        yield s"p/$name$ext" -> content,
        Range.linear(1, 8)
      )
      .map(_.toMap)

  private val genTime: Gen[Long] = Gen.long(Range.constant(0L, later))

  def propSameBytes: Property =
    for
      pickles <- genPickles.forAll
      order1 <- shuffle(pickles.toList).forAll
      order2 <- shuffle(pickles.toList).forAll
      time1 <- genTime.forAll
      time2 <- genTime.forAll
    yield withTempDir { dir =>
      write(dir.resolve("a.jar"), zip(dir.resolve("u1.jar"), time1, order1*))
      write(dir.resolve("b.jar"), zip(dir.resolve("u2.jar"), time2, order2*))
      Result.all(
        List(
          sameBytes(dir.resolve("a.jar"), dir.resolve("b.jar")),
          contents(dir.resolve("a.jar")) ==== pickles.toList.sortBy(_._1),
        )
      )
    }

  def propMerges: Property =
    for
      updates <- Gen.list(genPickles, Range.linear(1, 4)).forAll
      time <- genTime.forAll
    yield withTempDir { dir =>
      val jar = dir.resolve("early.jar")
      updates.zipWithIndex.foreach { (pickles, i) =>
        write(jar, zip(dir.resolve(s"u$i.jar"), time, pickles.toList*))
      }
      val direct = dir.resolve("direct.jar")
      write(direct, zip(dir.resolve("direct-u.jar"), 0L, updates.reduce(_ ++ _).toList*))
      Result.all(
        List(
          sameBytes(jar, direct),
          dir.toFile.list.toSet ==== Set("early.jar", "direct.jar"),
        )
      )
    }

  def propKnownProducts: Property =
    for
      pickles <- genPickles.forAll
      known <- Gen.list(Gen.boolean, Range.singleton(pickles.size)).forAll
    yield withTempDir { dir =>
      def classOf(pickle: String) = pickle.takeWhile(_ != '.') + ".class"
      val products = pickles.keys.toList.zip(known).collect { case (name, true) => classOf(name) }
      val jar = dir.resolve("early.jar")
      write(jar, zip(dir.resolve("u.jar"), 0L, pickles.toList*))
      PickleJar.write(jar, None, Some(products.toSet.asJava), noKeep, Logger.Null)
      contents(jar) ====
        pickles.toList.filter((name, _) => products.contains(classOf(name))).sortBy(_._1)
    }
end PickleJarSpec
