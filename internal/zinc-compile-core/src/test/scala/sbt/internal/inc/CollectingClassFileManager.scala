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

import xsbti.VirtualFile
import xsbti.compile.ClassFileManager

import java.io.File
import scala.collection.mutable

/**
 * Collection of `ClassFileManager`s used for testing purposes.
 */
class CollectingClassFileManager extends ClassFileManager {

  /** Collect generated classes, with public access to allow inspection. */
  val generatedClasses = new mutable.HashSet[VirtualFile]

  @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
  override def delete(classes: Array[File]): Unit = ()

  override def generated(classes: Array[VirtualFile]): Unit = {
    generatedClasses ++= classes
    ()
  }

  @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
  override def generated(classes: Array[File]): Unit =
    generated(classes.map(c => PlainVirtualFile(c.toPath): VirtualFile))

  override def complete(success: Boolean): Unit = ()
}
