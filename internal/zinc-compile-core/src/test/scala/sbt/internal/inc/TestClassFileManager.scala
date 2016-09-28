package sbt.internal.inc

import java.io.File

import xsbti.compile.ClassFileManager

import scala.collection.mutable

/**
 * Collection of `ClassFileManager`s used for testing purposes.
 */
class NoopClassFileManager extends ClassFileManager {
  override def delete(classes: Array[File]): Unit = ()
  override def generated(classes: Array[File]): Unit = ()
  override def complete(success: Boolean): Unit = ()
}

class CollectingClassFileManager extends NoopClassFileManager {
  /** Collect generated classes, with public access to allow inspection. */
  val generatedClasses = new mutable.HashSet[File]

  override def delete(classes: Array[File]): Unit = ()

  override def generated(classes: Array[File]): Unit = {
    generatedClasses ++= classes
    ()
  }

  override def complete(success: Boolean): Unit = ()
}