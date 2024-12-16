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

import java.io.File
import scala.collection.mutable
import xsbti.compile.ClassFileManager
import xsbti.VirtualFile
import org.scalatest.flatspec.AnyFlatSpec

class ClassFileManagerDefaultsSpec extends AnyFlatSpec {
  class TestClassFileManager extends ClassFileManager {
    val deletedFiles = mutable.HashSet.empty[File]
    val generatedFiles = mutable.HashSet.empty[File]
    override def delete(classes: Array[File]): Unit = {
      deletedFiles ++= classes
      ()
    }
    override def generated(classes: Array[File]): Unit = {
      generatedFiles ++= classes
      ()
    }
    override def complete(x: Boolean): Unit = {}
  }
  "VirtualFile apis" should "delegate" in {
    val manager = new TestClassFileManager
    val file = new File("foo")
    manager.delete(Array[VirtualFile](PlainVirtualFile(file.toPath)))
    manager.generated(Array[VirtualFile](PlainVirtualFile(file.toPath)))
    assert(manager.deletedFiles == mutable.HashSet(file))
    assert(manager.generatedFiles == mutable.HashSet(file))
  }
}
