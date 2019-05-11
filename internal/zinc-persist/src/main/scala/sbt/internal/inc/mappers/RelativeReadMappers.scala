/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc.mappers

import java.io.File
import java.nio.file.Path

import xsbti.compile.MiniSetup
import xsbti.compile.analysis.{ ReadMapper, RootPaths, Stamp }

// Please see docs about the implementation in the WriteMapper interface
final class NaiveRelativeReadMapper(rootProjectPath: Path) extends ReadMapper {
  private def reconstructRelative(file: File): File =
    MapperUtils.reconstructRelative(file, rootProjectPath)

  override def mapSourceFile(sourceFile: File): File = reconstructRelative(sourceFile)
  override def mapBinaryFile(binaryFile: File): File = reconstructRelative(binaryFile)
  override def mapProductFile(productFile: File): File = reconstructRelative(productFile)

  override def mapClasspathEntry(classpathEntry: File): File = reconstructRelative(classpathEntry)
  override def mapJavacOption(javacOption: String): String = javacOption
  override def mapScalacOption(scalacOption: String): String = scalacOption

  override def mapOutputDir(outputDir: File): File = reconstructRelative(outputDir)
  override def mapSourceDir(sourceDir: File): File = reconstructRelative(sourceDir)

  override def mapProductStamp(file: File, productStamp: Stamp): Stamp = productStamp
  override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = sourceStamp
  override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = binaryStamp

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
}

final class RelativeReadMapper(rootPaths: RootPaths) extends ReadMapper {
  import MapperUtils.reconstructRelative
  private final val sourcesRoot = rootPaths.getSourcesRootPath.toPath
  private final val librariesRoot = rootPaths.getLibrariesRootPath.toPath
  private final val productsRoot = rootPaths.getProductsRootPath.toPath

  override def mapSourceFile(sourceFile: File): File = reconstructRelative(sourceFile, sourcesRoot)
  override def mapBinaryFile(binaryFile: File): File =
    reconstructRelative(binaryFile, librariesRoot)
  override def mapProductFile(productFile: File): File =
    reconstructRelative(productFile, productsRoot)

  /**
   *
   * This function reconstructs a relative path to use the libraries root, assuming
   * that all the classpath entries are absolute (even compiler plugins) and they
   * come from a unique place.
   *
   * @param classpathEntry The classpath entry to be passed to the compiler.
   */
  override def mapClasspathEntry(classpathEntry: File): File =
    reconstructRelative(classpathEntry, librariesRoot)

  override def mapJavacOption(javacOption: String): String = javacOption
  override def mapScalacOption(scalacOption: String): String = scalacOption

  override def mapOutputDir(outputDir: File): File = reconstructRelative(outputDir, productsRoot)
  override def mapSourceDir(sourceDir: File): File = reconstructRelative(sourceDir, sourcesRoot)

  override def mapProductStamp(file: File, productStamp: Stamp): Stamp = productStamp
  override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = sourceStamp
  override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = binaryStamp

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
}
