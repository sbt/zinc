/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.mappers

import java.io.File
import java.nio.file.Path

import xsbti.compile.MiniSetup
import xsbti.compile.analysis.{ Stamp, WriteMapper }

// Please see docs about the implementation in the WriteMapper interface
/** @inheritdoc */
final class RelativeWriteMapper(rootProjectPath: Path) extends WriteMapper {
  private def makeRelative(file: File): File = MapperUtils.makeRelative(file, rootProjectPath)

  override def mapSourceFile(sourceFile: File): File = makeRelative(sourceFile)
  override def mapBinaryFile(binaryFile: File): File = makeRelative(binaryFile)
  override def mapProductFile(productFile: File): File = makeRelative(productFile)

  override def mapClasspathEntry(classpathEntry: File): File = makeRelative(classpathEntry)
  override def mapJavacOption(javacOption: String): String = javacOption
  override def mapScalacOption(scalacOption: String): String = scalacOption

  override def mapOutputDir(outputDir: File): File = makeRelative(outputDir)
  override def mapSourceDir(sourceDir: File): File = makeRelative(sourceDir)

  override def mapProductStamp(file: File, productStamp: Stamp): Stamp = productStamp
  override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = sourceStamp
  override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = binaryStamp

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
}
