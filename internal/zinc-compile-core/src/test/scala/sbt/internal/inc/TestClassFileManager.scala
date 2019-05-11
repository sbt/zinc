/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc

import java.io.File

import xsbti.compile.ClassFileManager

import scala.collection.mutable

/**
 * Collection of `ClassFileManager`s used for testing purposes.
 */
class CollectingClassFileManager extends ClassFileManager {

  /** Collect generated classes, with public access to allow inspection. */
  val generatedClasses = new mutable.HashSet[File]

  override def delete(classes: Array[File]): Unit = ()

  override def generated(classes: Array[File]): Unit = {
    generatedClasses ++= classes
    ()
  }

  override def complete(success: Boolean): Unit = ()
}
