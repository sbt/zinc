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

import java.nio.file.Path

import xsbti.compile.MiniSetup
import xsbti.compile.analysis.{ ReadMapper, RootPaths, Stamp }
import xsbti.VirtualFileRef

// Please see docs about the implementation in the ReadMapper interface
final class NaiveRelativeReadMapper(rootProjectPath: Path) extends ReadMapper {
  private def reconstructRelative(file: Path): Path =
    MapperUtils.reconstructRelative(file, rootProjectPath)

  override def mapSourceFile(sourceFile: VirtualFileRef): VirtualFileRef = sourceFile
  override def mapBinaryFile(binaryFile: VirtualFileRef): VirtualFileRef = binaryFile
  override def mapProductFile(productFile: VirtualFileRef): VirtualFileRef = productFile

  override def mapClasspathEntry(classpathEntry: Path): Path = reconstructRelative(classpathEntry)
  override def mapJavacOption(javacOption: String): String = javacOption
  override def mapScalacOption(scalacOption: String): String = scalacOption

  override def mapOutputDir(outputDir: Path): Path = reconstructRelative(outputDir)
  override def mapSourceDir(sourceDir: Path): Path = reconstructRelative(sourceDir)

  override def mapProductStamp(file: VirtualFileRef, productStamp: Stamp): Stamp = productStamp
  override def mapSourceStamp(file: VirtualFileRef, sourceStamp: Stamp): Stamp = sourceStamp
  override def mapBinaryStamp(file: VirtualFileRef, binaryStamp: Stamp): Stamp = binaryStamp

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
}

final class RelativeReadMapper(rootPaths: RootPaths) extends ReadMapper {
  import MapperUtils.reconstructRelative
  private final val sourcesRoot = rootPaths.getSourcesRootPath.toPath
  private final val librariesRoot = rootPaths.getLibrariesRootPath.toPath
  private final val productsRoot = rootPaths.getProductsRootPath.toPath

  override def mapSourceFile(sourceFile: VirtualFileRef): VirtualFileRef = sourceFile
  override def mapBinaryFile(binaryFile: VirtualFileRef): VirtualFileRef = binaryFile
  override def mapProductFile(productFile: VirtualFileRef): VirtualFileRef = productFile

  /**
   * Reconstructs a relative path to use the libraries root, assuming that all the classpath entries
   * are absolute (even compiler plugins) and they come from a unique place.
   *
   * @param classpathEntry The classpath entry to be passed to the compiler.
   */
  override def mapClasspathEntry(classpathEntry: Path): Path =
    reconstructRelative(classpathEntry, librariesRoot)

  override def mapJavacOption(javacOption: String): String = javacOption
  override def mapScalacOption(scalacOption: String): String = scalacOption

  override def mapOutputDir(outputDir: Path): Path = reconstructRelative(outputDir, productsRoot)
  override def mapSourceDir(sourceDir: Path): Path = reconstructRelative(sourceDir, sourcesRoot)

  override def mapProductStamp(file: VirtualFileRef, productStamp: Stamp): Stamp = productStamp
  override def mapSourceStamp(file: VirtualFileRef, sourceStamp: Stamp): Stamp = sourceStamp
  override def mapBinaryStamp(file: VirtualFileRef, binaryStamp: Stamp): Stamp = binaryStamp

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
}
