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
package classfile

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.nio.file.*
import java.nio.file.spi.FileSystemProvider
import scala.jdk.CollectionConverters.*
import HedgehogSupport.withTempDir

object IndexBasedZipFsOpsSpec extends Properties:
  private val XL = 0xffff // minimum size to be zip64, which I'm calling "XL"
  private val L = XL - 1 // last size to be standard zip, which I'm calling "L"

  override def tests: List[Test] = List(
    example("create XS jars", assertCreate(2)),
    example("merge  XS jars", assertMerge(2, 1)),
    example("shrink XS jars", assertShrink(3, 2)),
    example("create  L jars", assertCreate(L)),
    example("merge   L jars", assertMerge(L, 1)), //      breach threshold
    example("shrink  L jars", assertShrink(L + 1, L)), // breach threshold back
    example("create XL jars", assertCreate(XL)),
    example("merge  XL jars", assertMerge(XL, 1)),
    example("shrink XL jars", assertShrink(XL + 1, XL)),
    property("merge jars of any size", propMerge).withTests(20),
    property("shrink jars of any size", propShrink).withTests(20),
  )

  private lazy val zipFsProvider =
    FileSystemProvider.installedProviders().stream().filter(_.getScheme == "jar").findAny().get()

  private def assertCreate(size: Int): Result = withTempDir { dir =>
    assertSize(createJar(dir, size), size)
  }

  private def assertMerge(size1: Int, size2: Int): Result = withTempDir { dir =>
    val a = createJar(dir, size1)
    val b = createJar(dir, size2, "b", size1)
    IndexBasedZipFsOps.mergeArchives(a, b)
    assertSize(a, size1 + size2)
  }

  private def assertShrink(size1: Int, size2: Int): Result = withTempDir { dir =>
    val a = createJar(dir, size1)
    val files = for i <- size2 until size1 yield classFileName(i)
    IndexBasedZipFsOps.removeEntries(a.toFile, files)
    assertSize(a, size2)
  }

  private def assertSize(p: Path, size: Int): Result =
    IndexBasedZipFsOps.readCentralDir(p.toFile).getHeaders.size() ==== size

  def propMerge: Property =
    for
      size1 <- Gen.int(Range.linear(0, 200)).forAll
      size2 <- Gen.int(Range.linear(0, 200)).forAll
    yield assertMerge(size1, size2)

  def propShrink: Property =
    for
      size1 <- Gen.int(Range.linear(0, 200)).forAll
      size2 <- Gen.int(Range.linear(0, size1)).forAll
    yield assertShrink(size1, size2)

  private def createJar(dir: Path, n: Int, name: String = "a", from: Int = 0): Path =
    val out = dir.resolve(s"$name.jar")
    val zipfs = zipFsProvider.newFileSystem(out, Map("create" -> "true").asJava)
    val root = zipfs.getRootDirectories.iterator().next()
    for i <- from until (from + n) do
      Files.write(root.resolve(classFileName(i)), Array.emptyByteArray)
    zipfs.close()
    out

  private def classFileName(i: Int) = f"C$i%032d.class"
end IndexBasedZipFsOpsSpec
