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

import xsbti.VirtualFileRef
import xsbti.compile.MiniSetup
import xsbti.compile.analysis.{ RootPaths, Stamp, WriteMapper }

// Please see docs about the implementation in the WriteMapper interface
final class NaiveRelativeWriteMapper(rootProjectPath: Path) extends WriteMapper {
  private def makeRelative(file: Path): Path = MapperUtils.makeRelative(file, rootProjectPath)

  override def mapSourceFile(sourceFile: VirtualFileRef): VirtualFileRef = sourceFile
  override def mapBinaryFile(binaryFile: VirtualFileRef): VirtualFileRef = binaryFile
  override def mapProductFile(productFile: VirtualFileRef): VirtualFileRef = productFile

  override def mapClasspathEntry(classpathEntry: Path): Path = makeRelative(classpathEntry)
  override def mapJavacOption(javacOption: String): String = javacOption
  override def mapScalacOption(scalacOption: String): String = scalacOption

  override def mapOutputDir(outputDir: Path): Path = makeRelative(outputDir)
  override def mapSourceDir(sourceDir: Path): Path = makeRelative(sourceDir)

  override def mapProductStamp(file: VirtualFileRef, productStamp: Stamp): Stamp = productStamp
  override def mapSourceStamp(file: VirtualFileRef, sourceStamp: Stamp): Stamp = sourceStamp
  override def mapBinaryStamp(file: VirtualFileRef, binaryStamp: Stamp): Stamp = binaryStamp

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
}

final class RelativeWriteMapper(rootPaths: RootPaths) extends WriteMapper {
  import MapperUtils.makeRelative
  private final val sourcesRoot = rootPaths.getSourcesRootPath.toPath
  private final val librariesRoot = rootPaths.getLibrariesRootPath.toPath
  private final val productsRoot = rootPaths.getProductsRootPath.toPath

  override def mapSourceFile(sourceFile: VirtualFileRef): VirtualFileRef = sourceFile
  override def mapBinaryFile(binaryFile: VirtualFileRef): VirtualFileRef = binaryFile
  override def mapProductFile(productFile: VirtualFileRef): VirtualFileRef = productFile

  /**
   * Makes a path relative to the libraries root, assuming that all the classpath entries are
   * absolute (even compiler plugins) and they come from a unique place.
   *
   * @param classpathEntry The classpath entry to be passed to the compiler.
   */
  override def mapClasspathEntry(classpathEntry: Path): Path =
    makeRelative(classpathEntry, librariesRoot)

  override def mapJavacOption(javacOption: String): String = javacOption
  override def mapScalacOption(scalacOption: String): String = scalacOption

  override def mapOutputDir(outputDir: Path): Path = makeRelative(outputDir, productsRoot)
  override def mapSourceDir(sourceDir: Path): Path = makeRelative(sourceDir, sourcesRoot)

  override def mapProductStamp(file: VirtualFileRef, productStamp: Stamp): Stamp = productStamp
  override def mapSourceStamp(file: VirtualFileRef, sourceStamp: Stamp): Stamp = sourceStamp
  override def mapBinaryStamp(file: VirtualFileRef, binaryStamp: Stamp): Stamp = binaryStamp

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
}
