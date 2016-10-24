package sbt.internal.inc

import java.io.File

import xsbti.compile.ClassFileManager

/**
 * A noop ClassFileManager that applies to non-javac tools.
 */
class NoopClassFileManager extends ClassFileManager {
  override def delete(classes: Array[File]): Unit = ()
  override def generated(classes: Array[File]): Unit = ()
  override def useCustomizedFileManager(): Boolean = false
  override def complete(success: Boolean): Unit = ()
}
