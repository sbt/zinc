/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File
import java.nio.file.Path

import xsbti.compile.{ MiniOptions, MiniSetup }

import scala.util.Try

case class Mapper[V](read: String => V, write: V => String)
case class ContextAwareMapper[C, V](read: (C, String) => V, write: (C, V) => String)

object Mapper {
  val forFile: Mapper[File] = Mapper(FormatCommons.stringToFile, FormatCommons.fileToString)
  val forString: Mapper[String] = Mapper(identity, identity)
  val forStamp: ContextAwareMapper[File, Stamp] = ContextAwareMapper((_, v) => Stamp.fromString(v), (_, s) => s.toString)

  implicit class MapperOpts[V](mapper: Mapper[V]) {
    def map[T](map: V => T, unmap: T => V) = Mapper[T](mapper.read.andThen(map), unmap.andThen(mapper.write))
  }

  def rebaseFile(from: Path, to: Path): Mapper[File] = {
    def rebaseFile(from: Path, to: Path): File => File =
      f =>
        Try { to.resolve(from.relativize(f.toPath)).toFile }.getOrElse(f)

    forFile.map(rebaseFile(from, to), rebaseFile(to, from))
  }

  def relativizeFile(root: Path): Mapper[File] = {
    val header = "##"
    def write(f: File): String =
      Try {
        val relativePath = root.relativize(f.toPath).toString
        s"$header$relativePath"
      }.getOrElse(FormatCommons.fileToString(f))

    def read(string: String): File =
      if (string.startsWith(header)) root.resolve(string.drop(header.length)).toFile
      else FormatCommons.stringToFile(string)

    Mapper[File](read, write)
  }

  def updateModificationDateFileMapper(from: Mapper[File]): ContextAwareMapper[File, Stamp] =
    ContextAwareMapper(
      (binaryFile, _) => Stamp.lastModified(from.read(binaryFile.toString)),
      (_, stamp) => stamp.toString
    )
}

/** Maps Analysis and Minisetup on read/write to store. Used for caching purposes */
trait AnalysisMappers {
  val outputDirMapper: Mapper[File]
  val sourceDirMapper: Mapper[File]
  val scalacOptions: Mapper[String]
  val javacOptions: Mapper[String]

  val sourceMapper: Mapper[File]
  val productMapper: Mapper[File]
  val binaryMapper: Mapper[File]

  val binaryStampMapper: ContextAwareMapper[File, Stamp]
  val productStampMapper: ContextAwareMapper[File, Stamp]
  val sourceStampMapper: ContextAwareMapper[File, Stamp]

  val classpathMapper: Mapper[File]

  /** Function is called on to map MiniSetup that is imported from cache */
  def mapOptionsFromCache(fromCache: MiniSetup): MiniSetup
}

trait AnalysisMappersAdapter extends AnalysisMappers {
  override val outputDirMapper: Mapper[File] = Mapper.forFile
  override val sourceDirMapper: Mapper[File] = Mapper.forFile
  override val scalacOptions: Mapper[String] = Mapper.forString
  override val javacOptions: Mapper[String] = Mapper.forString

  override val sourceMapper: Mapper[File] = Mapper.forFile
  override val productMapper: Mapper[File] = Mapper.forFile
  override val binaryMapper: Mapper[File] = Mapper.forFile

  override val binaryStampMapper: ContextAwareMapper[File, Stamp] = Mapper.forStamp
  override val productStampMapper: ContextAwareMapper[File, Stamp] = Mapper.forStamp
  override val sourceStampMapper: ContextAwareMapper[File, Stamp] = Mapper.forStamp

  override val classpathMapper: Mapper[File] = Mapper.forFile

  override def mapOptionsFromCache(fromCache: MiniSetup): MiniSetup = fromCache
}

object AnalysisMappers {
  val default: AnalysisMappers = new AnalysisMappersAdapter {}
}