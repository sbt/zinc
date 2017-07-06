package sbt.internal.inc.mappers

import java.io.File
import java.nio.file.Path

import sbt.inc.WriteMapper
import xsbti.compile.MiniSetup
import xsbti.compile.analysis.Stamp

final class RelativeWriteMapper(rootProjectPath: Path) extends WriteMapper {
  private def makeRelative(file: File): File = MapperUtils.makeRelative(file, rootProjectPath)

  override def mapSourceFile(sourceFile: File): File = makeRelative(sourceFile)
  override def mapBinaryFile(binaryFile: File): File = makeRelative(binaryFile)
  override def mapProductFile(productFile: File): File = makeRelative(productFile)

  override def mapClasspathEntry(classpathEntry: File): File = makeRelative(classpathEntry)
  override def mapJavacOption(javacOption: String): String = identity(javacOption)
  override def mapScalacOption(scalacOption: String): String = identity(scalacOption)

  override def mapOutputDir(outputDir: File): File = makeRelative(outputDir)
  override def mapSourceDir(sourceDir: File): File = makeRelative(sourceDir)

  override def mapProductStamp(file: File, productStamp: Stamp): Stamp = identity(productStamp)
  override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = identity(sourceStamp)
  override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = identity(binaryStamp)

  override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = identity(miniSetup)
}
