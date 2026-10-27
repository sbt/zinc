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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import xsbti.VirtualFile
import HedgehogSupport.withTempDir

object MappedFileConverterInterningSpec extends Properties:
  override def tests: List[Test] = List(
    example("share interned items across repeated directory conversions", shareItems),
    example("convert an item fresh once its file is rewritten", rewrittenItem),
    example("reflect directory content changes in a fresh conversion", contentChanges),
    example("convert fresh once a missing path exists", missingPath),
    example("keep fresh-instance semantics for top-level conversions", topLevel),
    property("share every item of any directory across conversions", propShareItems)
      .withTests(20),
  )

  private def shareItems: Result = withTempDir { dir =>
    val classes = Files.createDirectories(dir.resolve("classes"))
    Files.write(classes.resolve("A.class"), "a".getBytes("UTF-8"))
    Files.write(classes.resolve("B.class"), "bb".getBytes("UTF-8"))
    val converter = MappedFileConverter.empty
    withDirectories(converter.toVirtualFile(classes), converter.toVirtualFile(classes)) {
      (first, second) =>
        Result.all(
          List(
            Result.assert(second ne first),
            first.items.size ==== 2,
            Result.assert(first.items.lazyZip(second.items).forall(_ eq _)),
          )
        )
    }
  }

  private def rewrittenItem: Result = withTempDir { dir =>
    val classes = Files.createDirectories(dir.resolve("classes"))
    val file = Files.write(classes.resolve("A.class"), "aaaa".getBytes("UTF-8"))
    val converter = MappedFileConverter.empty
    withDirectory(converter.toVirtualFile(classes)) { firstDir =>
      val first = firstDir.items.head
      val firstHash = first.contentHash
      Files.write(file, "bbbb".getBytes("UTF-8")) // same size
      Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000))
      withDirectory(converter.toVirtualFile(classes)) { secondDir =>
        val second = secondDir.items.head
        Result.all(
          List(
            Result.assert(second ne first),
            Result.diff(second.contentHash, firstHash)(_ != _),
          )
        )
      }
    }
  }

  private def contentChanges: Result = withTempDir { dir =>
    val classes = Files.createDirectories(dir.resolve("classes"))
    Files.write(classes.resolve("A.class"), "a".getBytes("UTF-8"))
    val converter = MappedFileConverter.empty
    withDirectory(converter.toVirtualFile(classes)) { first =>
      Files.write(classes.resolve("B.class"), "bb".getBytes("UTF-8"))
      withDirectory(converter.toVirtualFile(classes)) { second =>
        Result.all(List(first.items.size ==== 1, second.items.size ==== 2))
      }
    }
  }

  private def missingPath: Result = withTempDir { dir =>
    val classes = dir.resolve("out").resolve("classes")
    val converter = MappedFileConverter.empty
    withDirectory(converter.toVirtualFile(classes)) { missing =>
      Files.createDirectories(classes)
      Files.write(classes.resolve("A.class"), "a".getBytes("UTF-8"))
      withDirectory(converter.toVirtualFile(classes)) { second =>
        Result.all(List(missing.items.size ==== 0, second.items.size ==== 1))
      }
    }
  }

  private def topLevel: Result = withTempDir { dir =>
    val file = Files.write(dir.resolve("A.scala"), "object A".getBytes("UTF-8"))
    val converter = MappedFileConverter.empty
    val first = converter.toVirtualFile(file)
    val second = converter.toVirtualFile(file)
    Result.assert(second ne first)
  }

  private val genEntries: Gen[Map[String, String]] =
    Gen
      .list(
        for
          name <- Gen.string(Gen.lower, Range.linear(1, 8))
          content <- Gen.string(Gen.alphaNum, Range.linear(0, 16))
        yield s"$name.class" -> content,
        Range.linear(0, 8)
      )
      .map(_.toMap)

  def propShareItems: Property =
    for entries <- genEntries.forAll
    yield withTempDir { dir =>
      val classes = Files.createDirectories(dir.resolve("classes"))
      entries.foreach((name, content) =>
        Files.write(classes.resolve(name), content.getBytes("UTF-8"))
      )
      val converter = MappedFileConverter.empty
      withDirectories(converter.toVirtualFile(classes), converter.toVirtualFile(classes)) {
        (first, second) =>
          Result.all(
            List(
              first.items.size ==== entries.size,
              second.items.size ==== entries.size,
              Result.assert(first.items.lazyZip(second.items).forall(_ eq _)),
            )
          )
      }
    }

  private def withDirectory(vf: VirtualFile)(f: MappedDirectory => Result): Result =
    vf match
      case dir: MappedDirectory => f(dir)
      case other                => Result.failure.log(s"expected a MappedDirectory, got $other")

  private def withDirectories(a: VirtualFile, b: VirtualFile)(
      f: (MappedDirectory, MappedDirectory) => Result
  ): Result =
    withDirectory(a)(first => withDirectory(b)(second => f(first, second)))
end MappedFileConverterInterningSpec
