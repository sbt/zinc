package sbt.internal.inc.cached

import java.io.File
import java.nio.file.Path

import sbt.internal.inc._
import sbt.io.IO
import xsbti.compile.{ CompileAnalysis, MiniSetup }

trait CompilationCache {
  def loadCache(projectLocation: File): Option[(CompileAnalysis, MiniSetup)]
}

case class ProjectRebasedCache(remoteRoot: Path, cacheLocation: Path) extends CompilationCache {
  override def loadCache(projectLocation: File): Option[(CompileAnalysis, MiniSetup)] = {
    val mapper = createMapper(remoteRoot, projectLocation.toPath)
    FileBasedStore(cacheLocation.toFile, mapper).get() match {
      case Some((originalAnalysis: Analysis, originalSetup)) =>
        originalAnalysis.stamps.products.keySet.foreach { originalFile =>
          val targetFile = new File(mapper.productMapper.write(originalFile))
          IO.copyFile(targetFile, originalFile, preserveLastModified = true)
        }

        Some(originalAnalysis -> originalSetup)
      case _ => None
    }
  }

  private def createMapper(from: Path, to: Path): AnalysisMappers = new AnalysisMappers {
    private def justRebase = Mapper.rebaseFile(from, to)

    override val outputDirMapper: Mapper[File] = justRebase
    override val sourceDirMapper: Mapper[File] = justRebase
    override val sourceMapper: Mapper[File] = justRebase
    override val productMapper: Mapper[File] = justRebase
    override val binaryMapper: Mapper[File] = justRebase

    override val binaryStampMapper: ContextAwareMapper[File, Stamp] =
      Mapper.updateModificationDateFileMapper(binaryMapper)
  }
}