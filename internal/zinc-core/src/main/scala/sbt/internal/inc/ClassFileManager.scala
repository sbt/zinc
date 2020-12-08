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

package sbt
package internal
package inc

import sbt.io.IO
import xsbti.compile.{ ClassFileManager => XClassFileManager, _ }
import xsbti.{ PathBasedFile, VirtualFile }

import java.io.File
import java.nio.file.{ Files, Path }
import java.util.Optional
import scala.collection.mutable

object ClassFileManager {
  def getDefaultClassFileManager(
      classFileManagerType: Optional[ClassFileManagerType],
      auxiliaryClassFiles: Array[AuxiliaryClassFiles]
  ): XClassFileManager = {
    if (classFileManagerType.isPresent) {
      classFileManagerType.get match {
        case _: DeleteImmediatelyManagerType =>
          new DeleteClassFileManager(auxiliaryClassFiles)
        case m: TransactionalManagerType =>
          transactional(m.backupDirectory.toPath, Array.empty, m.logger)
      }
    } else new DeleteClassFileManager(auxiliaryClassFiles)
  }

  def getDefaultClassFileManager(
      classFileManagerType: Optional[ClassFileManagerType],
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      auxiliaryClassFiles: Array[AuxiliaryClassFiles]
  ): XClassFileManager = {
    if (classFileManagerType.isPresent) {
      classFileManagerType.get match {
        case _: DeleteImmediatelyManagerType =>
          deleteImmediately(output, outputJarContent, auxiliaryClassFiles)
        case m: TransactionalManagerType =>
          transactional(
            output,
            outputJarContent,
            m.backupDirectory.toPath,
            auxiliaryClassFiles,
            m.logger
          )
      }
    } else deleteImmediately(output, outputJarContent, auxiliaryClassFiles)
  }

  def getClassFileManager(
      options: IncOptions,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent
  ): XClassFileManager = {
    import sbt.internal.inc.JavaInterfaceUtil.{ EnrichOption, EnrichOptional }
    val internal =
      getDefaultClassFileManager(
        options.classfileManagerType,
        output,
        outputJarContent,
        options.auxiliaryClassFiles
      )
    val external = Option(options.externalHooks()).flatMap(_.getExternalClassFileManager.toOption)
    xsbti.compile.WrappedClassFileManager.of(internal, external.toOptional)
  }

  private final class DeleteClassFileManager(auxiliaryFiles: Array[AuxiliaryClassFiles])
      extends XClassFileManager {
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def delete(classes: Array[File]): Unit = deleteImpl(classes.toVector)
    override def delete(classes: Array[VirtualFile]): Unit = {
      deleteImpl(classes.toVector.map(toPath).map(_.toFile))
    }

    private def deleteImpl(classes: Vector[File]): Unit = {
      val allFiles = classes ++ classes.flatMap(associatedFiles)
      IO.deleteFilesEmptyDirs(allFiles)
    }

    override def generated(classes: Array[VirtualFile]): Unit = ()
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def generated(classes: Array[File]): Unit = {}
    override def complete(success: Boolean): Unit = ()

    private def associatedFiles(classFile: File): Iterable[File] =
      auxiliaryFiles.flatMap(_.associatedFiles(classFile.toPath)).map(_.toFile)
  }

  private def toPath(vf: VirtualFile): Path =
    vf match {
      case x: PathBasedFile => x.toPath
      case x                => sys.error(s"${x.id} is not path-based")
    }

  /**
   * Constructs a minimal [[ClassFileManager]] implementation that immediately deletes
   * class files when they are requested. This is the default implementation of the class
   * file manager by the Scala incremental compiler if no class file manager is specified.
   */
  def deleteImmediately(auxiliaryClassFiles: Array[AuxiliaryClassFiles]): XClassFileManager =
    new DeleteClassFileManager(auxiliaryClassFiles)

  def deleteImmediatelyFromJar(
      outputJar: Path,
      outputJarContent: JarUtils.OutputJarContent,
      auxiliaryClassFiles: Array[AuxiliaryClassFiles],
  ): XClassFileManager =
    new DeleteClassFileManagerForJar(outputJar, outputJarContent, auxiliaryClassFiles)

  def deleteImmediately(
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      auxiliaryClassFiles: Array[AuxiliaryClassFiles]
  ): XClassFileManager = {
    val outputJar = JarUtils.getOutputJar(output)
    outputJar.fold(deleteImmediately(auxiliaryClassFiles))(
      deleteImmediatelyFromJar(_, outputJarContent, auxiliaryClassFiles)
    )
  }

  /**
   * Constructs a transactional [[ClassFileManager]] implementation that restores class
   * files to the way they were before compilation if there is an error. Otherwise, it
   * keeps the successfully generated class files from the new compilation.
   *
   * This is the default class file manager used by sbt, and makes sense in a lot of scenarios.
   */
  def transactional(
      tempDir0: Path,
      auxiliaryClassFiles: Array[AuxiliaryClassFiles],
      logger: sbt.util.Logger
  ): XClassFileManager =
    new TransactionalClassFileManager(tempDir0, auxiliaryClassFiles, logger)

  def transactionalForJar(
      outputJar: Path,
      outputJarContent: JarUtils.OutputJarContent,
      auxiliaryClassFiles: Array[AuxiliaryClassFiles]
  ): XClassFileManager = {
    new TransactionalClassFileManagerForJar(
      outputJar,
      outputJarContent,
      auxiliaryClassFiles.toVector
    )
  }

  def transactional(
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      tempDir: Path,
      auxiliaryClassFiles: Array[AuxiliaryClassFiles],
      logger: sbt.util.Logger
  ): XClassFileManager = {
    val outputJar = JarUtils.getOutputJar(output)
    outputJar.fold(
      transactional(tempDir, auxiliaryClassFiles, logger)
    )(transactionalForJar(_, outputJarContent, auxiliaryClassFiles))
  }

  private final class TransactionalClassFileManager(
      tempDir0: Path,
      auxiliaryFiles: Array[AuxiliaryClassFiles],
      logger: sbt.util.Logger
  ) extends XClassFileManager {
    private val tempDir = tempDir0.normalize
    IO.delete(tempDir.toFile)
    Files.createDirectories(tempDir)
    logger.debug(s"Created transactional ClassFileManager with tempDir = $tempDir")

    private[this] val generatedFiles = new mutable.HashSet[File]
    private[this] val movedFiles = new mutable.HashMap[File, File]

    private def showFiles(files: Iterable[File]): String =
      files.map(f => s"\t${f.getName}").mkString("\n")

    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def delete(classes: Array[File]): Unit = deleteImpl(classes.toVector)
    override def delete(classes: Array[VirtualFile]): Unit =
      deleteImpl(classes.toVector.map(c => toPath(c).toFile))
    private def deleteImpl(classes: Vector[File]): Unit = {
      val allFiles = classes ++ classes.flatMap(associatedFiles)

      logger.debug(s"About to delete class files:\n${showFiles(allFiles)}")

      val toBeBackedUp =
        allFiles.filter(c => c.exists && !movedFiles.contains(c) && !generatedFiles(c))
      logger.debug(s"We backup class files:\n${showFiles(toBeBackedUp)}")
      for { c <- toBeBackedUp } movedFiles.put(c, move(c))
      IO.deleteFilesEmptyDirs(allFiles)
    }

    override def generated(classes0: Array[VirtualFile]): Unit =
      generatedImpl(classes0.toVector.map(c => toPath(c).toFile))
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def generated(classes: Array[File]): Unit = generatedImpl(classes.toVector)
    private def generatedImpl(classes: Vector[File]): Unit = {
      val allFiles = classes ++ classes.flatMap(associatedFiles)
      logger.debug(s"Registering generated classes:\n${showFiles(allFiles)}")
      generatedFiles ++= allFiles
      ()
    }

    override def complete(success: Boolean): Unit = {
      if (!success) {
        logger.debug("Rolling back changes to class files.")
        logger.debug(s"Removing generated classes:\n${showFiles(generatedFiles)}")
        IO.deleteFilesEmptyDirs(generatedFiles.toVector)
        logger.debug(s"Restoring class files: \n${showFiles(movedFiles.keys)}")
        for {
          (orig, tmp) <- movedFiles
        } {
          if (tmp.exists) {
            if (!orig.getParentFile.exists) {
              IO.createDirectory(orig.getParentFile)
            } // if
            IO.move(tmp, orig)
          } // if
        }
      }
      logger.debug(s"Removing the temporary directory used for backing up class files: $tempDir")
      IO.delete(tempDir.toFile)
    }

    private def associatedFiles(classFile: File): Iterable[File] =
      auxiliaryFiles.flatMap(_.associatedFiles(classFile.toPath)).map(_.toFile)

    def move(c: File): File = {
      val target = Files.createTempFile(tempDir, "sbt", ".class").toFile
      IO.move(c, target)
      target
    }
  }

  private final class DeleteClassFileManagerForJar(
      outputJar: Path,
      outputJarContent: JarUtils.OutputJarContent,
      auxiliaryFiles: Array[AuxiliaryClassFiles],
  ) extends XClassFileManager {
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def delete(classes: Array[File]): Unit = deleteImpl(classes.toVector)
    override def delete(classes0: Array[VirtualFile]): Unit =
      deleteImpl(classes0.toVector.map(toPath(_).toFile))
    private def deleteImpl(classes: Vector[File]): Unit = {
      val allFiles = classes ++ classes.flatMap(associatedFiles)
      val relClasses = allFiles.map(c => JarUtils.ClassInJar.fromFile(c).toClassFilePath.get)
      outputJarContent.removeClasses(relClasses.toSet)
      JarUtils.removeFromJar(outputJar, relClasses)
    }
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def generated(classes: Array[File]): Unit = ()
    override def generated(classes: Array[VirtualFile]): Unit = ()
    override def complete(success: Boolean): Unit = ()

    private def associatedFiles(classFile: File): Iterable[File] =
      auxiliaryFiles.flatMap(_.associatedFiles(classFile.toPath)).map(_.toFile)
  }

  /**
   * Version of [[sbt.internal.inc.ClassFileManager.TransactionalClassFileManager]]
   * that works when sources are compiled directly to a jar file.
   *
   * Before compilation the index is read from the output jar if it exists
   * and after failed compilation it is reverted. This implementation relies
   * on the fact that nothing is actually removed from jar during incremental
   * compilation. Files are only removed from index or new files are appended
   * and potential overwrite is also handled by replacing index entry. For this
   * reason the old index with offsets to old files will still be valid.
   */
  private final class TransactionalClassFileManagerForJar(
      outputJar: Path,
      outputJarContent: JarUtils.OutputJarContent,
      auxiliaryFiles: Vector[AuxiliaryClassFiles]
  ) extends XClassFileManager {
    private val backedUpIndex = Some(outputJar)
      .filter(Files.exists(_))
      .map(JarUtils.stashIndex)

    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def delete(classesInJar: Array[File]): Unit = {
      val classes =
        classesInJar.toVector
          .map(c => JarUtils.ClassInJar.fromPath(c.toPath).toClassFilePath.get)
      JarUtils.removeFromJar(outputJar, classes)
      outputJarContent.removeClasses(classes.toSet)
    }
    override def delete(classes: Array[VirtualFile]): Unit = {
      val classesFiles = classes.toVector.map(toPath)
      val allFiles = classesFiles ++ classesFiles.flatMap(associatedFiles)
      val filesInJar =
        allFiles.map(c => JarUtils.ClassInJar.fromPath(c).toClassFilePath.get)
      JarUtils.removeFromJar(outputJar, filesInJar)
      outputJarContent.removeClasses(filesInJar.toSet)
    }

    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def generated(classes: Array[File]): Unit = ()
    override def generated(classes: Array[VirtualFile]): Unit = ()

    override def complete(success: Boolean): Unit = {
      if (!success) {
        backedUpIndex.foreach(index => JarUtils.unstashIndex(outputJar, index))
      }
    }

    private def associatedFiles(classFile: Path): Iterable[Path] =
      auxiliaryFiles.flatMap(_.associatedFiles(classFile))
  }
}
