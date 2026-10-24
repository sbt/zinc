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
import java.io.File
import scala.collection.mutable
import xsbti.compile.ClassFileManager
import xsbti.VirtualFile

object ClassFileManagerDefaultsSpec extends Properties:
  override def tests: List[Test] = List(
    example("VirtualFile apis should delegate", delegate),
    property("VirtualFile apis should delegate every file", propDelegate),
  )

  class TestClassFileManager extends ClassFileManager:
    val deletedFiles = mutable.HashSet.empty[File]
    val generatedFiles = mutable.HashSet.empty[File]
    override def delete(classes: Array[File]): Unit =
      deletedFiles ++= classes
      ()
    override def generated(classes: Array[File]): Unit =
      generatedFiles ++= classes
      ()
    override def complete(x: Boolean): Unit = {}

  private def check(files: List[File]): Result =
    val manager = new TestClassFileManager
    manager.delete(files.map(f => PlainVirtualFile(f.toPath): VirtualFile).toArray)
    manager.generated(files.map(f => PlainVirtualFile(f.toPath): VirtualFile).toArray)
    Result.all(
      List(
        manager.deletedFiles.toSet ==== files.toSet,
        manager.generatedFiles.toSet ==== files.toSet,
      )
    )

  private def delegate: Result = check(List(new File("foo")))

  def propDelegate: Property =
    for
      names <- Gen
        .list(
          Gen.list(Gen.string(Gen.alphaNum, Range.linear(1, 8)), Range.linear(1, 3)),
          Range.linear(0, 8)
        )
        .forAll
    yield check(names.map(segments => new File(segments.mkString(File.separator))))
end ClassFileManagerDefaultsSpec
