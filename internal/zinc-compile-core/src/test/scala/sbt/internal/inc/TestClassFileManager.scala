package sbt.internal.inc

import java.io.File

import xsbti.compile.NoopClassFileManager

import scala.collection.mutable

/**
 * Collection of `ClassFileManager`s used for testing purposes.
 */
class CollectingClassFileManager extends NoopClassFileManager {
  /** Collect generated classes, with public access to allow inspection. */
  val generatedClasses = new mutable.HashSet[File]

  override def generated(classes: Array[File]): Unit = {
    generatedClasses ++= classes
    ()
  }
}