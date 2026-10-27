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
import java.nio.file.{ Files, Path }
import sbt.io.IO
import xsbti.{ PathBasedFile, VirtualFile }

object MappedFileConverterRefSpec extends Properties:
  override def tests: List[Test] = List(
    example("a directory under a root is encoded without listing it", directoryNotListed),
    example("a machine path is rejected unless machine paths are allowed", machinePath),
    example("PlainVirtualFileConverter falls back to toVirtualFile", plainFallback),
    property("the id agrees with toVirtualFile", propAgreesWithToVirtualFile),
  )

  private def directoryNotListed: Result = withTempDir { dir =>
    val out = Files.createDirectories(dir.resolve("out"))
    val classes = Files.createDirectories(out.resolve("a/classes"))
    Files.write(classes.resolve("A.class"), "a".getBytes("UTF-8"))
    val converter = MappedFileConverter(Map("OUT" -> out), allowMachinePath = false)
    val ref = converter.toVirtualFileRef(classes)
    Result.all(
      List(
        ref.id ==== "${OUT}/a/classes",
        Result.assert(!ref.isInstanceOf[VirtualFile]).log(s"ref: ${ref.getClass}"),
      )
    )
  }

  private def machinePath: Result = withTempDir { dir =>
    val outside = dir.resolve("other/meta")
    val roots = Map("OUT" -> dir.resolve("out"))
    val strict = MappedFileConverter(roots, allowMachinePath = false)
    val lenient = MappedFileConverter(roots, allowMachinePath = true)
    val rejected =
      try
        strict.toVirtualFileRef(outside)
        false
      catch case _: RuntimeException => true
    Result.all(
      List(
        Result.assert(rejected).log("strict converter accepted a machine path"),
        lenient.toVirtualFileRef(outside).id ==== outside.toString.replace('\\', '/'),
      )
    )
  }

  private def plainFallback: Result = withTempDir { dir =>
    val p = dir.resolve("meta")
    val converter = PlainVirtualFileConverter.converter
    val ref = converter.toVirtualFileRef(p)
    ref match
      case f: PathBasedFile =>
        Result.all(List(ref.id ==== converter.toVirtualFile(p).id, f.toPath ==== p))
      case _ => Result.failure.log(s"not path based: ${ref.getClass}")
  }

  private def genSegments: Gen[List[String]] =
    Gen.list(Gen.string(Gen.alphaNum, Range.linear(1, 8)), Range.linear(1, 4))

  def propAgreesWithToVirtualFile: Property =
    for
      underRoot <- Gen.boolean.forAll
      segments <- genSegments.forAll
      fileName <- Gen.element1("x.jar", "meta", "classes").forAll
      exists <- Gen.boolean.forAll
    yield withTempDir { dir =>
      val out = Files.createDirectories(dir.resolve("out"))
      val base = if underRoot then out else dir.resolve("elsewhere")
      val path = segments.foldLeft(base)(_.resolve(_)).resolve(fileName)
      if exists then
        Files.createDirectories(path.getParent)
        Files.write(path, "x".getBytes("UTF-8"))
      val converter = MappedFileConverter(Map("OUT" -> out), allowMachinePath = true)
      (converter.toVirtualFileRef(path).id ==== converter.toVirtualFile(path).id)
        .log(s"path: $path")
    }

  private def withTempDir(f: Path => Result): Result =
    IO.withTemporaryDirectory(d => f(d.toPath))
end MappedFileConverterRefSpec
