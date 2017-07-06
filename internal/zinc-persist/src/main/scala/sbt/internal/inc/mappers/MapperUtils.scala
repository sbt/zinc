package sbt.internal.inc.mappers

import java.io.File
import java.nio.file.Path

import xsbti.compile.analysis.Stamp

object MapperUtils {
  private[inc] def rebase(target: File, from: Path, to: Path): File = {
    to.resolve(from.relativize(target.toPath)).toFile
  }

  private[inc] def makeRelative(file: File, from: Path): File = {
    from.relativize(file.toPath).toFile
  }

  private[inc] def reconstructRelative(file: File, from: Path): File = {
    if (file.isAbsolute) file
    else from.resolve(file.toPath).toFile
  }

  private[inc] def recomputeModificationDate(previouslyStampedFile: File): Stamp = {
    sbt.internal.inc.Stamper.forLastModified(previouslyStampedFile)
  }
}
