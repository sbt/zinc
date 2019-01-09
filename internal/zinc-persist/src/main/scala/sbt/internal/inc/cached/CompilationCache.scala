/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.cached

import java.io.File
import java.nio.file.Path

import sbt.internal.inc._
import sbt.internal.inc.mappers.MapperUtils
import sbt.io.IO
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{ CompileAnalysis, MiniSetup }

// TODO(jvican): Consider removing this interface or at least document it.
trait CompilationCache {
  def mappers(projectLocation: File): ReadWriteMappers
  def loadCache(projectLocation: File): Option[(CompileAnalysis, MiniSetup)]
}

case class ProjectRebasedCache(remoteRoot: Path, cacheLocation: Path) extends CompilationCache {

  override def mappers(projectLocation: File) =
    ReadWriteMappers.getMachineIndependentMappers(projectLocation.toPath)

  override def loadCache(projectLocation: File): Option[(CompileAnalysis, MiniSetup)] = {
    import JavaInterfaceUtil.EnrichOptional
    import scala.collection.JavaConverters._
    val projectLocationPath = projectLocation.toPath
    val store = FileAnalysisStore.binary(cacheLocation.toFile, mappers(projectLocation))
    store.get().toOption match {
      case Some(analysisContents) =>
        val originalAnalysis = analysisContents.getAnalysis
        val originalSetup = analysisContents.getMiniSetup
        val allProductsStamps = originalAnalysis.readStamps().getAllProductStamps.keySet.asScala
        allProductsStamps.foreach { (targetFile: File) =>
          // NB: Analysis has already been rewritten to align with the target location of the
          // project. We rewrite the path back to the cache location here to find our input file.
          val originalFile = MapperUtils.rebase(targetFile, projectLocationPath, remoteRoot)
          IO.copyFile(originalFile, targetFile, preserveLastModified = true)
        }

        Some(originalAnalysis -> originalSetup)
      case _ => None
    }
  }
}
