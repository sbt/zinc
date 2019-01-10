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
import xsbti.compile.analysis.{ RootPaths, Stamp, WriteMapper }

// Please see docs about the implementation in the WriteMapper interface
final class NaiveRelativeWriteMapper(rootProjectPath: Path) extends WriteMapper {
  private def makeRelative(file: File): File = MapperUtils.makeRelative(file, rootProjectPath)

  private[this] val javaHome = System.getProperty("java.home")

  override def acceptBinaryFile(binaryFile: File) = !binaryFile.toString.startsWith(javaHome)

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

final class RelativeWriteMapper(rootPaths: RootPaths) extends WriteMapper {
  import MapperUtils.makeRelative
  private final val sourcesRoot = rootPaths.getSourcesRootPath.toPath
  private final val librariesRoot = rootPaths.getLibrariesRootPath.toPath
  private final val productsRoot = rootPaths.getProductsRootPath.toPath

  private[this] val javaHome = System.getProperty("java.home")

  override def acceptBinaryFile(binaryFile: File) = !binaryFile.toString.startsWith(javaHome)

  override def mapSourceFile(sourceFile: File): File = makeRelative(sourceFile, sourcesRoot)
  override def mapBinaryFile(binaryFile: File): File = makeRelative(binaryFile, librariesRoot)
  override def mapProductFile(productFile: File): File = makeRelative(productFile, productsRoot)

  /**
   * This function makes a path relative to the libraries root, assuming that all
   * the classpath entries are absolute (even compiler plugins) and they come from
   * a unique place.
   *
   * @param classpathEntry The classpath entry to be passed to the compiler.
   */
  override def mapClasspathEntry(classpathEntry: File): File =
    makeRelative(classpathEntry, librariesRoot)

  override def mapJavacOption(javacOption: String): String = javacOption
  override def mapScalacOption(scalacOption: String): String = scalacOption

  override def mapOutputDir(outputDir: File): File = makeRelative(outputDir, productsRoot)
  override def mapSourceDir(sourceDir: File): File = makeRelative(sourceDir, sourcesRoot)

  override def mapProductStamp(file: File, productStamp: Stamp): Stamp = productStamp
  override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = sourceStamp
  override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = binaryStamp

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = miniSetup
}
