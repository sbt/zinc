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
package javac

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.{ BasicFileAttributes, FileTime }

import sbt.io.PathFinder

import scala.util.control.NonFatal

/**
 * Cheap identity of a class file. javac rewrites every class file it produces, so a rewritten
 * file always gets a new time. Unlike `sbt.internal.inc.Stamp` this is never persisted; it is only
 * compared within one javac run.
 */
final case class ClassStamp(lastModified: FileTime, size: Long)

object ClassStamp {

  /** None when the attributes cannot be read, e.g. the file vanished after being listed. */
  def of(path: Path): Option[ClassStamp] =
    try {
      val attrs = Files.readAttributes(path, classOf[BasicFileAttributes])
      Some(ClassStamp(attrs.lastModifiedTime, attrs.size))
    } catch {
      case NonFatal(_) => None
    }
}

trait Classes {
  def paths: Seq[Path]

  /** Each class file with its stamp, or None when the attributes cannot be read. */
  private def stamped: Seq[(Path, Option[ClassStamp])] = paths.map(p => p -> ClassStamp.of(p))

  /** Stamps of `paths`, keyed by path string so jar entries compare across filesystem instances. */
  def stamps: Map[String, ClassStamp] =
    stamped.collect { case (p, Some(stamp)) => p.toString -> stamp }.toMap
  final def stampsAndClose(): Map[String, ClassStamp] = {
    try stamps
    finally close()
  }

  /**
   * The class files that are new or were rewritten since `old` was taken (sbt/zinc#586). Times are
   * compared exactly, unlike `Stamp.equivStamp`: a spurious difference only adds a class file that
   * JavaAnalyze maps to no compiled source and drops. A file whose stamp cannot be read counts as
   * changed, as every listed file did before stamps existed.
   */
  def changedSince(old: Map[String, ClassStamp]): Seq[Path] =
    stamped.collect { case (p, stamp) if stamp.forall(s => !old.get(p.toString).contains(s)) => p }

  def close(): Unit = ()
}
object Classes {
  object empty extends Classes {
    override def paths: Seq[Path] = Nil
  }
}

trait ClassFinder {
  def classes: Classes
}

class DirectoryClassFinder(dir: Path) extends ClassFinder {
  class DirectoryClasses(val paths: Seq[Path]) extends Classes

  private val pathFinder = PathFinder(dir.toFile) ** "*.class"

  override def classes: DirectoryClasses = new DirectoryClasses(pathFinder.get().map(_.toPath))
}

class JarClassFinder(jar: Path) extends ClassFinder {
  class JarClasses(val paths: Seq[Path], fs: FileSystem) extends Classes {
    override def close(): Unit = {
      fs.close()
      super.close()
    }
  }

  override def classes: Classes =
    if (Files.exists(jar)) {
      val jarFs = FileSystems.newFileSystem(jar, null.asInstanceOf[ClassLoader])
      try {
        val stream = Files.find(
          jarFs.getRootDirectories.iterator().next(),
          Int.MaxValue,
          (path, _) => path.toString.endsWith(".class")
        )
        try {
          val builder = Seq.newBuilder[Path]
          stream.forEachOrdered((a: Path) => builder += a)
          new JarClasses(builder.result(), jarFs)
        } finally stream.close()
      } catch {
        case NonFatal(t) =>
          jarFs.close()
          throw t
      }
    } else Classes.empty
}
