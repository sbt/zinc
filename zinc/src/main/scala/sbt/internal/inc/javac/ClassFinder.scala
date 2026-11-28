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

import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.{ BasicFileAttributes, FileTime }
import java.util.EnumSet

import scala.util.control.NonFatal

/**
 * Last-modified time and size of a class file. Unlike `Stamp`, it is compared exactly. A rewrite
 * to the same size within the timestamp granularity of the file system has an equal stamp.
 * A jar entry keeps its time when it is rewritten, so it is stamped with its CRC-32 as well.
 */
private[javac] final case class ClassStamp(lastModified: FileTime, size: Long, crc: Option[Long])

private[javac] object ClassStamp:
  def apply(attrs: BasicFileAttributes): ClassStamp =
    ClassStamp(attrs.lastModifiedTime, attrs.size, crc = None)

  def of(path: Path): Option[ClassStamp] =
    try Some(ClassStamp(Files.readAttributes(path, classOf[BasicFileAttributes])))
    catch
      case NonFatal(_) => None
end ClassStamp

trait Classes:
  def paths: Seq[Path]

  @deprecated("internal", "2.1.1")
  final def pathsAndClose(): Seq[Path] =
    try paths
    finally close()

  /**
   * The stamps of `paths`, keyed by `Path#toString`. A `Path` of a jar equals no `Path` of the
   * same jar opened again, so the paths themselves cannot be the keys.
   */
  private[javac] def stamps: Map[String, ClassStamp] =
    paths.flatMap(p => ClassStamp.of(p).map(p.toString -> _)).toMap

  private[javac] final def stampsAndClose(): Map[String, ClassStamp] =
    try stamps
    finally close()

  /**
   * The class files that are new or rewritten since `old` was taken. Zinc deletes only tracked
   * products before javac runs, so javac can rewrite untracked ones in place (sbt/zinc#586). A
   * file whose stamp cannot be read counts as changed; a rewrite keeping time and size is missed.
   */
  private[javac] def changedSince(old: Map[String, ClassStamp]): Seq[Path] =
    val current = stamps
    paths.filter: p =>
      val key = p.toString
      current.get(key).forall(stamp => !old.get(key).contains(stamp))
  def close(): Unit = ()
end Classes
object Classes:
  object empty extends Classes:
    override def paths: Seq[Path] = Nil

  /** The class files under `root` with the stamps that the walk itself reads. */
  private[javac] def find(root: Path): Seq[(Path, ClassStamp)] =
    val found = Seq.newBuilder[(Path, ClassStamp)]
    val visitor = new SimpleFileVisitor[Path]:
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
        if attrs.isRegularFile && file.toString.endsWith(".class") then
          found += file -> ClassStamp(attrs)
        FileVisitResult.CONTINUE
      override def visitFileFailed(file: Path, e: IOException): FileVisitResult =
        FileVisitResult.CONTINUE
    Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Int.MaxValue, visitor)
    found.result()

  private[javac] def keyed(found: Seq[(Path, ClassStamp)]): Map[String, ClassStamp] =
    found.map((path, stamp) => path.toString -> stamp).toMap
end Classes

trait ClassFinder:
  def classes: Classes

class DirectoryClassFinder(dir: Path) extends ClassFinder:
  class DirectoryClasses private[javac] (
      val paths: Seq[Path],
      found: Option[Seq[(Path, ClassStamp)]]
  ) extends Classes:
    def this(paths: Seq[Path]) = this(paths, None)
    override private[javac] def stamps: Map[String, ClassStamp] =
      found.fold(super.stamps)(Classes.keyed)

  override def classes: DirectoryClasses =
    val found = Classes.find(dir)
    new DirectoryClasses(found.map(_._1), Some(found))

class JarClassFinder(jar: Path) extends ClassFinder:
  class JarClasses private[javac] (
      val paths: Seq[Path],
      fs: FileSystem,
      found: Option[Seq[(Path, ClassStamp)]]
  ) extends Classes:
    def this(paths: Seq[Path], fs: FileSystem) = this(paths, fs, None)
    override private[javac] def stamps: Map[String, ClassStamp] =
      found.fold(super.stamps)(Classes.keyed)
    override def close(): Unit =
      fs.close()
      super.close()

  private def crcOf(entry: Path): Option[Long] =
    try
      Files.getAttribute(entry, "zip:crc") match
        case crc: java.lang.Long => Some(crc.longValue)
        case _                   => None
    catch
      case NonFatal(_) => None

  override def classes: Classes =
    if Files.exists(jar) then
      val jarFs = FileSystems.newFileSystem(jar, null.asInstanceOf[ClassLoader])
      try
        val found = Classes
          .find(jarFs.getRootDirectories.iterator().next())
          .map((entry, stamp) => entry -> stamp.copy(crc = crcOf(entry)))
        new JarClasses(found.map(_._1), jarFs, Some(found))
      catch
        case NonFatal(t) =>
          jarFs.close()
          throw t
    else Classes.empty
end JarClassFinder
