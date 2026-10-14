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
import java.util.Arrays
import java.util.jar.{ Attributes, JarOutputStream, Manifest }
import java.util.zip.ZipEntry

import org.scalacheck.{ Gen, Prop, Properties }
import org.scalacheck.Prop.forAllNoShrink
import sbt.io.IO.withTemporaryDirectory as withTmpDir
import sbt.util.Logger
import scala.jdk.CollectionConverters.*
import scala.util.Using
import xsbti.compile.{ CompileOrder, MiniOptions, MiniSetup }

object EarlyOutputRollbackSpecification extends Properties("EarlyOutputRollback"):
  private type Pickles = Map[String, String]

  private val setup = MiniSetup.of(
    CompileOutput.empty,
    MiniOptions.of(Array(), Array(), Array()),
    "3.9.0",
    CompileOrder.Mixed,
    true,
    Array()
  )

  private val pickleName =
    Gen.oneOf("p/A.tasty", "p/B.tasty", "p/C.tasty", "p/D.tasty", "p/E.tasty", "p/F.tasty")
  private val content = Gen.oneOf("a", "b", "ab", "ab" * 40, "abc" * 30)
  private def pickles(min: Int, max: Int): Gen[Pickles] =
    Gen.choose(min, max).flatMap(Gen.listOfN(_, Gen.zip(pickleName, content))).map(_.toMap)
  private val write: Gen[(Pickles, Option[Set[String]])] =
    for
      updates <- pickles(1, 2)
      known <- Gen.frequency(
        3 -> Gen.const(None),
        1 -> Gen.choose(0, 6).flatMap(Gen.listOfN(_, pickleName)).map(ns => Some(ns.toSet))
      )
    yield (updates, known)

  property("restores the early jar after writes from any offset") = forAllNoShrink(
    Gen.frequency(4 -> Gen.const(true), 1 -> Gen.const(false)),
    pickles(3, 6),
    Gen.choose(2, 5).flatMap(Gen.listOfN(_, write))
  )((existed, initial, writes) => failedCompile(existed, initial, writes)._1)

  property("restores the early jar after writes that start before the earlier ones") =
    Prop.delay {
      val (result, several) = failedCompile(
        existed = true,
        Map("p/A.tasty" -> "a", "p/B.tasty" -> "b", "p/C.tasty" -> "c"),
        List(Map("p/C.tasty" -> "c2") -> None, Map("p/A.tasty" -> "a2") -> None)
      )
      result && (Prop(several) :| "rolled back one copy")
    }

  property("restores the early jar on an interrupted thread") = Prop.delay {
    withTmpDir { tmp =>
      val jar = tmp.toPath.resolve("early.jar")
      pickleWrite(jar, Map("p/A.tasty" -> "a", "p/B.tasty" -> "b"), None, (_, _) => false)
      val original = Files.readAllBytes(jar)
      val rollback = EarlyOutputRollback(Some(jar), None, setup)
      pickleWrite(jar, Map("p/A.tasty" -> "a2"), None, rollback.keep)
      Thread.currentThread.interrupt()
      rollback.rollback(Logger.Null)
      val interrupted = Thread.interrupted()
      rollback.discard()
      Prop.all(
        Prop(interrupted) :| "the interrupt was lost",
        Prop(Arrays.equals(Files.readAllBytes(jar), original)) :| "not restored",
      )
    }
  }

  /**
   * Writes `writes` into an early jar that holds `initial` if it `existed`, as one compile, and
   * rolls them back. Also tells whether the rollback kept several copies.
   */
  private def failedCompile(
      existed: Boolean,
      initial: Pickles,
      writes: List[(Pickles, Option[Set[String]])]
  ): (Prop, Boolean) =
    withTmpDir { tmp =>
      val dir = tmp.toPath
      val jar = dir.resolve("early/early.jar")
      Files.createDirectories(jar.getParent)
      if existed then pickleWrite(jar, initial, None, (_, _) => false)
      val original = Option.when(existed)(Files.readAllBytes(jar))
      val rollback = EarlyOutputRollback(Some(jar), None, setup)
      var froms = List.empty[Long]
      val keep: (Path, Long) => Boolean = (copy, from) =>
        froms :+= from
        rollback.keep(copy, from)
      var expected: Pickles = if existed then initial else Map.empty
      val written = writes.zipWithIndex.map { case ((updates, known), i) =>
        pickleWrite(jar, updates, known, keep)
        expected = (expected ++ updates).filter((n, _) => known.forall(_(n)))
        val fresh = dir.resolve(s"fresh/$i.jar")
        pickleWrite(fresh, expected, None, (_, _) => false)
        Prop(Arrays.equals(Files.readAllBytes(jar), Files.readAllBytes(fresh))) :|
          s"write $i differs from one write of ${expected.keys.toList.sorted}"
      }
      rollback.rollback(Logger.Null)
      rollback.discard()
      val empty = dir.resolve("fresh/empty.jar")
      PickleJar.write(empty, None, None, (_, _) => false, Logger.Null)
      val restored = Files.readAllBytes(jar)
      val left = jar.getParent.toFile.list.toList
      val result = Prop.all(
        (written ++ List(
          Prop(Arrays.equals(restored, original.getOrElse(Files.readAllBytes(empty)))) :|
            s"rolled back from ${froms.mkString(", ")}",
          Prop(left == List("early.jar")) :| s"left $left",
        ))*
      )
      val newFirsts = froms.zip(froms.scanLeft(Long.MaxValue)(_ min _)).count(_ < _)
      (result, existed && newFirsts > 1)
    }

  private def pickleWrite(
      jar: Path,
      pickles: Pickles,
      known: Option[Set[String]],
      keep: (Path, Long) => Boolean
  ): Unit =
    Files.createDirectories(jar.getParent)
    val updates = jar.resolveSibling("updates.jar")
    val manifest = Manifest()
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    val out = JarOutputStream(BufferedOutputStream(Files.newOutputStream(updates)), manifest)
    Using.resource(out) { out =>
      pickles.foreach { (name, content) =>
        out.putNextEntry(ZipEntry(name))
        out.write(content.getBytes(UTF_8))
        out.closeEntry()
      }
    }
    val products = known.map(_.map(_.stripSuffix(".tasty") + ".class").asJava)
    PickleJar.write(jar, Some(updates), products, keep, Logger.Null)
end EarlyOutputRollbackSpecification
