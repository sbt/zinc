/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File

case class Mapper[V](read: String => V, write: V => String)

object Mapper {
  val forFile: Mapper[File] = Mapper(FormatCommons.stringToFile, FormatCommons.fileToString)
  val forString: Mapper[String] = Mapper(identity, identity)
  val forStamp: Mapper[Stamp] = Mapper(Stamp.fromString, _.toString)
}

trait AnalysisMappers {
  val outputDirMapper: Mapper[File] = Mapper.forFile
  val sourceDirMapper: Mapper[File] = Mapper.forFile
  val scalacOptions: Mapper[String] = Mapper.forString

  val sourceMapper: Mapper[File] = Mapper.forFile
  val productMapper: Mapper[File] = Mapper.forFile
  val binaryMapper: Mapper[File] = Mapper.forFile

  val binaryStampMapper: Mapper[Stamp] = Mapper.forStamp
  val productStampMapper: Mapper[Stamp] = Mapper.forStamp
  val sourceStampMapper: Mapper[Stamp] = Mapper.forStamp

}

object AnalysisMappers {
  val default = new AnalysisMappers {}
}