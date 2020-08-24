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
import java.io.File
import java.util.Optional
import java.nio.file.{ Files, Path }

import collection.mutable
import xsbti.{ FileConverter, PathBasedFile, VirtualFile }
import xsbti.compile.{
  IncOptions,
  DeleteImmediatelyManagerType,
  TransactionalManagerType,
  ClassFileManagerType,
  ClassFileManager => XClassFileManager,
  Output
}

object ClassFileManager {
  def getDefaultClassFileManager(
      classFileManagerType: Optional[ClassFileManagerType]
  ): XClassFileManager = {
    if (classFileManagerType.isPresent) {
      classFileManagerType.get match {
        case _: DeleteImmediatelyManagerType => new DeleteClassFileManager
        case m: TransactionalManagerType =>
          transactional(m.backupDirectory.toPath, m.logger)
      }
    } else new DeleteClassFileManager
  }

  def getDefaultClassFileManager(
      classFileManagerType: Optional[ClassFileManagerType],
      output: Output,
      outputJarContent: JarUtils.OutputJarContent
  ): XClassFileManager = {
    if (classFileManagerType.isPresent) {
      classFileManagerType.get match {
        case _: DeleteImmediatelyManagerType => deleteImmediately(output, outputJarContent)
        case m: TransactionalManagerType =>
          transactional(output, outputJarContent, m.backupDirectory.toPath, m.logger)
      }
    } else deleteImmediately(output, outputJarContent)
  }

  def getClassFileManager(
      options: IncOptions,
      converter: FileConverter,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent
  ): XClassFileManager = {
    import sbt.internal.inc.JavaInterfaceUtil.{ EnrichOptional, EnrichOption }
    val internal =
      getDefaultClassFileManager(options.classfileManagerType, output, outputJarContent)
    val external = Option(options.externalHooks()).flatMap(_.getExternalClassFileManager.toOption)
    xsbti.compile.WrappedClassFileManager.of(internal, external.toOptional)
  }

  private final class DeleteClassFileManager extends XClassFileManager {
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def delete(classes: Array[File]): Unit = IO.deleteFilesEmptyDirs(classes)
    override def delete(classes: Array[VirtualFile]): Unit =
      IO.deleteFilesEmptyDirs(classes.toVector.map(toPath).map(_.toFile))
    override def generated(classes: Array[VirtualFile]): Unit = ()
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def generated(classes: Array[File]): Unit = {}
    override def complete(success: Boolean): Unit = ()
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
  def deleteImmediately: XClassFileManager = new DeleteClassFileManager

  def deleteImmediatelyFromJar(
      outputJar: Path,
      outputJarContent: JarUtils.OutputJarContent
  ): XClassFileManager =
    new DeleteClassFileManagerForJar(outputJar, outputJarContent)

  def deleteImmediately(
      output: Output,
      outputJarContent: JarUtils.OutputJarContent
  ): XClassFileManager = {
    val outputJar = JarUtils.getOutputJar(output)
    outputJar.fold(deleteImmediately)(deleteImmediatelyFromJar(_, outputJarContent))
  }

  /**
   * Constructs a transactional [[ClassFileManager]] implementation that restores class
   * files to the way they were before compilation if there is an error. Otherwise, it
   * keeps the successfully generated class files from the new compilation.
   *
   * This is the default class file manager used by sbt, and makes sense in a lot of scenarios.
   */
  def transactional(tempDir0: Path, logger: sbt.util.Logger): XClassFileManager =
    new TransactionalClassFileManager(tempDir0, logger)

  def transactionalForJar(
      outputJar: Path,
      outputJarContent: JarUtils.OutputJarContent
  ): XClassFileManager = {
    new TransactionalClassFileManagerForJar(outputJar, outputJarContent)
  }

  def transactional(
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      tempDir: Path,
      logger: sbt.util.Logger
  ): XClassFileManager = {
    val outputJar = JarUtils.getOutputJar(output)
    outputJar.fold(transactional(tempDir, logger))(transactionalForJar(_, outputJarContent))
  }

  private final class TransactionalClassFileManager(tempDir0: Path, logger: sbt.util.Logger)
      extends XClassFileManager {
    val tempDir = tempDir0.normalize
    IO.delete(tempDir.toFile)
    Files.createDirectories(tempDir)
    logger.debug(s"Created transactional ClassFileManager with tempDir = $tempDir")

    private[this] val generatedClasses = new mutable.HashSet[File]
    private[this] val movedClasses = new mutable.HashMap[File, File]

    private def showFiles(files: Iterable[File]): String =
      files.map(f => s"\t${f.getName()}").mkString("\n")

    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def delete(classes0: Array[File]): Unit = deleteImpl(classes0)
    override def delete(classes: Array[VirtualFile]): Unit =
      deleteImpl(classes.map(c => toPath(c).toFile))
    private def deleteImpl(classes0: Array[File]): Unit = {
      logger.debug(s"About to delete class files:\n${showFiles(classes0)}")
      val toBeBackedUp =
        classes0.toVector.filter(
          c => c.exists && !movedClasses.contains(c) && !generatedClasses(c)
        )
      logger.debug(s"We backup class files:\n${showFiles(toBeBackedUp)}")
      for { c <- toBeBackedUp } {
        movedClasses.put(c, move(c))
      }
      IO.deleteFilesEmptyDirs(classes0)
    }

    override def generated(classes0: Array[VirtualFile]): Unit =
      generatedImpl(classes0.map(c => toPath(c).toFile))
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def generated(classes: Array[File]): Unit = generatedImpl(classes)
    private def generatedImpl(classes: Array[File]): Unit = {
      logger.debug(s"Registering generated classes:\n${showFiles(classes)}")
      generatedClasses ++= classes
      ()
    }

    override def complete(success: Boolean): Unit = {
      if (!success) {
        logger.debug("Rolling back changes to class files.")
        logger.debug(s"Removing generated classes:\n${showFiles(generatedClasses)}")
        IO.deleteFilesEmptyDirs(generatedClasses.toVector)
        logger.debug(s"Restoring class files: \n${showFiles(movedClasses.keys)}")
        for {
          (orig, tmp) <- movedClasses
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

    def move(c: File): File = {
      val target = Files.createTempFile(tempDir, "sbt", ".class").toFile
      IO.move(c, target)
      target
    }
  }

  private final class DeleteClassFileManagerForJar(
      outputJar: Path,
      outputJarContent: JarUtils.OutputJarContent
  ) extends XClassFileManager {
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def delete(classes: Array[File]): Unit = deleteImpl(classes)
    override def delete(classes0: Array[VirtualFile]): Unit =
      deleteImpl(classes0.map(toPath(_).toFile))
    private def deleteImpl(classes: Array[File]): Unit = {
      val relClasses = classes.map(c => JarUtils.ClassInJar.fromFile(c).toClassFilePath.get)
      outputJarContent.removeClasses(relClasses.toSet)
      JarUtils.removeFromJar(outputJar, relClasses)
    }
    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def generated(classes: Array[File]): Unit = ()
    override def generated(classes: Array[VirtualFile]): Unit = ()
    override def complete(success: Boolean): Unit = ()
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
      outputJarContent: JarUtils.OutputJarContent
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
    override def delete(classesInJar: Array[VirtualFile]): Unit = {
      val classes =
        classesInJar.toVector
          .map(toPath)
          .map(c => JarUtils.ClassInJar.fromPath(c).toClassFilePath.get)
      JarUtils.removeFromJar(outputJar, classes)
      outputJarContent.removeClasses(classes.toSet)
    }

    @deprecated("Use variant that takes Array[VirtualFile]", "1.4.0")
    override def generated(classes: Array[File]): Unit = ()
    override def generated(classes: Array[VirtualFile]): Unit = ()

    override def complete(success: Boolean): Unit = {
      if (!success) {
        backedUpIndex.foreach(index => JarUtils.unstashIndex(outputJar, index))
      }
    }
  }
}
