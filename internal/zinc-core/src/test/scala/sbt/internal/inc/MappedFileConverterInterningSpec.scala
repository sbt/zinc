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

package sbt
package internal
package inc

import java.nio.file.{ Files, Path }
import java.nio.file.attribute.FileTime
import sbt.io.IO
import xsbti.VirtualFile

class MappedFileConverterInterningSpec extends UnitSpec {

  behavior of "MappedFileConverter directory item interning"

  it should "share interned items across repeated directory conversions" in withTempDir { dir =>
    val classes = Files.createDirectories(dir.resolve("classes"))
    Files.write(classes.resolve("A.class"), "a".getBytes("UTF-8"))
    Files.write(classes.resolve("B.class"), "bb".getBytes("UTF-8"))
    val converter = MappedFileConverter.empty
    val first = directory(converter.toVirtualFile(classes))
    val second = directory(converter.toVirtualFile(classes))
    assert(second ne first)
    first.items should have size 2
    first.items.lazyZip(second.items).foreach((a, b) => assert(b eq a))
  }

  it should "convert an item fresh once its file is rewritten" in withTempDir { dir =>
    val classes = Files.createDirectories(dir.resolve("classes"))
    val file = Files.write(classes.resolve("A.class"), "aaaa".getBytes("UTF-8"))
    val converter = MappedFileConverter.empty
    val first = directory(converter.toVirtualFile(classes)).items.head
    val firstHash = first.contentHash
    Files.write(file, "bbbb".getBytes("UTF-8")) // same size
    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000))
    val second = directory(converter.toVirtualFile(classes)).items.head
    assert(second ne first)
    assert(second.contentHash != firstHash)
  }

  it should "reflect directory content changes in a fresh conversion" in withTempDir { dir =>
    val classes = Files.createDirectories(dir.resolve("classes"))
    Files.write(classes.resolve("A.class"), "a".getBytes("UTF-8"))
    val converter = MappedFileConverter.empty
    val first = directory(converter.toVirtualFile(classes))
    Files.write(classes.resolve("B.class"), "bb".getBytes("UTF-8"))
    val second = directory(converter.toVirtualFile(classes))
    first.items should have size 1
    second.items should have size 2
  }

  it should "convert fresh once a missing path exists" in withTempDir { dir =>
    val classes = dir.resolve("out").resolve("classes")
    val converter = MappedFileConverter.empty
    val missing = directory(converter.toVirtualFile(classes))
    missing.items shouldBe empty
    Files.createDirectories(classes)
    Files.write(classes.resolve("A.class"), "a".getBytes("UTF-8"))
    val second = directory(converter.toVirtualFile(classes))
    second.items should have size 1
  }

  it should "keep fresh-instance semantics for top-level conversions" in withTempDir { dir =>
    val file = Files.write(dir.resolve("A.scala"), "object A".getBytes("UTF-8"))
    val converter = MappedFileConverter.empty
    val first = converter.toVirtualFile(file)
    val second = converter.toVirtualFile(file)
    assert(second ne first)
  }

  private def withTempDir(f: Path => Unit): Unit = {
    val dir = Files.createTempDirectory("mapped-file-converter-interning")
    try f(dir)
    finally IO.delete(dir.toFile)
  }

  private def directory(vf: VirtualFile): MappedDirectory =
    vf match {
      case dir: MappedDirectory => dir
      case other                => fail(s"expected a MappedDirectory, got $other")
    }
}
