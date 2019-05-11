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

package sbt.internal.inc.cached

import java.io.File
import java.nio.file.Path

import sbt.internal.inc._
import sbt.internal.inc.mappers.MapperUtils
import sbt.io.IO
import xsbti.compile.analysis.{ ReadMapper, ReadWriteMappers, Stamp, WriteMapper }
import xsbti.compile.{ CompileAnalysis, MiniSetup }

trait CompilationCache {
  // TODO(jvican): Consider removing this interface or at least document it.
  def loadCache(projectLocation: File): Option[(CompileAnalysis, MiniSetup)]
}

case class ProjectRebasedCache(remoteRoot: Path, cacheLocation: Path) extends CompilationCache {
  override def loadCache(projectLocation: File): Option[(CompileAnalysis, MiniSetup)] = {
    import JavaInterfaceUtil.EnrichOptional
    import scala.collection.JavaConverters._
    val projectLocationPath = projectLocation.toPath
    val readMapper = new RebaseReadWriteMapper(remoteRoot, projectLocationPath)
    val writeMapper = new RebaseReadWriteMapper(projectLocationPath, remoteRoot)
    val mappers = new ReadWriteMappers(readMapper, writeMapper)
    val store = FileAnalysisStore.binary(cacheLocation.toFile, mappers)
    store.get().toOption match {
      case Some(analysisContents) =>
        val originalAnalysis = analysisContents.getAnalysis
        val originalSetup = analysisContents.getMiniSetup
        val allProductsStamps = originalAnalysis.readStamps().getAllProductStamps.keySet.asScala
        allProductsStamps.foreach { (originalFile: File) =>
          val targetFile = writeMapper.mapProductFile(originalFile)
          IO.copyFile(targetFile, originalFile, preserveLastModified = true)
        }

        Some(originalAnalysis -> originalSetup)
      case _ => None
    }
  }

  final class RebaseReadWriteMapper(from: Path, to: Path) extends ReadMapper with WriteMapper {
    private def rebase(file: File): File = MapperUtils.rebase(file, from, to)

    override def mapSourceFile(sourceFile: File): File = rebase(sourceFile)
    override def mapBinaryFile(binaryFile: File): File = rebase(binaryFile)
    override def mapProductFile(productFile: File): File = rebase(productFile)

    override def mapClasspathEntry(classpathEntry: File): File = rebase(classpathEntry)
    override def mapJavacOption(javacOption: String): String = identity(javacOption)
    override def mapScalacOption(scalacOption: String): String = identity(scalacOption)

    override def mapOutputDir(outputDir: File): File = rebase(outputDir)
    override def mapSourceDir(sourceDir: File): File = rebase(sourceDir)

    override def mapProductStamp(file: File, productStamp: Stamp): Stamp = identity(productStamp)
    override def mapSourceStamp(file: File, sourceStamp: Stamp): Stamp = identity(sourceStamp)
    override def mapBinaryStamp(file: File, binaryStamp: Stamp): Stamp = identity(binaryStamp)

    override def mapMiniSetup(miniSetup: MiniSetup): MiniSetup = identity(miniSetup)
  }
}
