/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.mappers

import java.io.File
import java.nio.file.Path

import sbt.inc.{ ReadMapper }
import xsbti.compile.MiniSetup
import xsbti.compile.analysis.Stamp

// Please see docs about the implementation in the WriteMapper interface
/** @inheritdoc */
final class RelativeReadMapper(rootProjectPath: Path) extends ReadMapper {
  private def reconstructRelative(file: File): File =
    MapperUtils.reconstructRelative(file, rootProjectPath)

  override def mapSourceFile(sourceFile: File): File = reconstructRelative(sourceFile)
  override def mapBinaryFile(binaryFile: File): File = reconstructRelative(binaryFile)
  override def mapProductFile(productFile: File): File = reconstructRelative(productFile)

  override def mapClasspathEntry(classpathEntry: File): File = reconstructRelative(classpathEntry)
  override def mapJavacOption(javacOption: String): String = identity(javacOption)
  override def mapScalacOption(scalacOption: String): String = identity(scalacOption)

  override def mapOutputDir(outputDir: File): File = reconstructRelative(outputDir)
  override def mapSourceDir(sourceDir: File): File = reconstructRelative(sourceDir)

  override def mapProductStamp(file: File, productStamp: Stamp): Stamp = identity(productStamp)
  override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = identity(sourceStamp)
  override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = identity(binaryStamp)

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = identity(miniSetup)
}
