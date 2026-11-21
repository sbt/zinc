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

/** Last-modified time and size of a class file. Unlike `Stamp`, it is compared exactly. */
private[javac] final case class ClassStamp(lastModified: FileTime, size: Long)

private[javac] object ClassStamp:
  def of(path: Path): Option[ClassStamp] =
    try
      val attrs = Files.readAttributes(path, classOf[BasicFileAttributes])
      Some(ClassStamp(attrs.lastModifiedTime, attrs.size))
    catch
      case NonFatal(_) => None

trait Classes:
  def paths: Seq[Path]
  private[javac] final def stampsAndClose(): Map[Path, ClassStamp] =
    try paths.flatMap(p => ClassStamp.of(p).map(p -> _)).toMap
    finally close()

  /**
   * The class files that are new or rewritten since `old` was taken. Zinc deletes only tracked
   * products before javac runs, so javac can rewrite untracked ones in place (sbt/zinc#586). A
   * file whose stamp cannot be read counts as changed; a rewrite keeping time and size is missed.
   */
  private[javac] def changedSince(old: Map[Path, ClassStamp]): Seq[Path] =
    paths.filter { p =>
      val stamp = ClassStamp.of(p)
      stamp.isEmpty || old.get(p) != stamp
    }
  def close(): Unit = ()
object Classes:
  object empty extends Classes:
    override def paths: Seq[Path] = Nil

trait ClassFinder:
  def classes: Classes

class DirectoryClassFinder(dir: Path) extends ClassFinder:
  class DirectoryClasses(val paths: Seq[Path]) extends Classes

  private val pathFinder = PathFinder(dir.toFile) ** "*.class"

  override def classes: DirectoryClasses = new DirectoryClasses(pathFinder.get().map(_.toPath))

class JarClassFinder(jar: Path) extends ClassFinder:
  class JarClasses(val paths: Seq[Path], fs: FileSystem) extends Classes:
    override def close(): Unit =
      fs.close()
      super.close()

  override def classes: Classes =
    if Files.exists(jar) then
      val jarFs = FileSystems.newFileSystem(jar, null.asInstanceOf[ClassLoader])
      try
        val stream = Files.find(
          jarFs.getRootDirectories.iterator().next(),
          Int.MaxValue,
          (path, _) => path.toString.endsWith(".class")
        )
        try
          val builder = Seq.newBuilder[Path]
          stream.forEachOrdered((a: Path) => builder += a)
          new JarClasses(builder.result(), jarFs)
        finally stream.close()
      catch
        case NonFatal(t) =>
          jarFs.close()
          throw t
    else Classes.empty
end JarClassFinder
