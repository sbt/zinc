/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import sbt.io.IO
import java.io.File
import java.util.Optional

import collection.mutable
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
      classFileManagerType: Optional[ClassFileManagerType]): XClassFileManager = {
    if (classFileManagerType.isPresent) {
      classFileManagerType.get match {
        case _: DeleteImmediatelyManagerType => new DeleteClassFileManager
        case m: TransactionalManagerType =>
          transactional(m.backupDirectory, m.logger)
      }
    } else new DeleteClassFileManager
  }

  def getClassFileManager(options: IncOptions): XClassFileManager = {
    import sbt.internal.inc.JavaInterfaceUtil.{ EnrichOptional, EnrichOption }
    val internal = getDefaultClassFileManager(options.classfileManagerType)
    val external = Option(options.externalHooks())
      .flatMap(ext => ext.getExternalClassFileManager.toOption)
    xsbti.compile.WrappedClassFileManager.of(internal, external.toOptional)
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
          transactional(output, outputJarContent, m.backupDirectory, m.logger)
      }
    } else deleteImmediately(output, outputJarContent)
  }

  def getClassFileManager(
      options: IncOptions,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent
  ): XClassFileManager = {
    import sbt.internal.inc.JavaInterfaceUtil.{ EnrichOptional, EnrichOption }
    val internal =
      getDefaultClassFileManager(options.classfileManagerType, output, outputJarContent)
    val external = Option(options.externalHooks())
      .flatMap(ext => ext.getExternalClassFileManager.toOption)
    xsbti.compile.WrappedClassFileManager.of(internal, external.toOptional)
  }

  private final class DeleteClassFileManager extends XClassFileManager {
    override def delete(classes: Array[File]): Unit =
      IO.deleteFilesEmptyDirs(classes)
    override def generated(classes: Array[File]): Unit = ()
    override def complete(success: Boolean): Unit = ()
  }

  /**
   * Constructs a minimal [[ClassFileManager]] implementation that immediately deletes
   * class files when they are requested. This is the default implementation of the class
   * file manager by the Scala incremental compiler if no class file manager is specified.
   */
  def deleteImmediately: XClassFileManager = new DeleteClassFileManager

  def deleteImmediatelyFromJar(outputJar: File,
                               outputJarContent: JarUtils.OutputJarContent): XClassFileManager =
    new DeleteClassFileManagerForJar(outputJar, outputJarContent)

  def deleteImmediately(output: Output,
                        outputJarContent: JarUtils.OutputJarContent): XClassFileManager = {
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
  def transactional(tempDir0: File, logger: sbt.util.Logger): XClassFileManager =
    new TransactionalClassFileManager(tempDir0, logger)

  def transactionalForJar(outputJar: File,
                          outputJarContent: JarUtils.OutputJarContent): XClassFileManager = {
    new TransactionalClassFileManagerForJar(outputJar, outputJarContent)
  }

  def transactional(
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      tempDir: File,
      logger: sbt.util.Logger
  ): XClassFileManager = {
    val outputJar = JarUtils.getOutputJar(output)
    outputJar.fold(transactional(tempDir, logger))(transactionalForJar(_, outputJarContent))
  }

  private final class TransactionalClassFileManager(tempDir0: File, logger: sbt.util.Logger)
      extends XClassFileManager {
    val tempDir = tempDir0.getCanonicalFile
    IO.delete(tempDir)
    IO.createDirectory(tempDir)
    logger.debug(s"Created transactional ClassFileManager with tempDir = $tempDir")

    private[this] val generatedClasses = new mutable.HashSet[File]
    private[this] val movedClasses = new mutable.HashMap[File, File]

    private def showFiles(files: Iterable[File]): String =
      files.map(f => s"\t$f").mkString("\n")

    override def delete(classes: Array[File]): Unit = {
      logger.debug(s"About to delete class files:\n${showFiles(classes)}")
      val toBeBackedUp =
        classes.filter(c => c.exists && !movedClasses.contains(c) && !generatedClasses(c))
      logger.debug(s"We backup class files:\n${showFiles(toBeBackedUp)}")
      for (c <- toBeBackedUp) {
        movedClasses.put(c, move(c))
      }
      IO.deleteFilesEmptyDirs(classes)
    }

    override def generated(classes: Array[File]): Unit = {
      logger.debug(s"Registering generated classes:\n${showFiles(classes)}")
      generatedClasses ++= classes
      ()
    }

    override def complete(success: Boolean): Unit = {
      if (!success) {
        logger.debug("Rolling back changes to class files.")
        logger.debug(s"Removing generated classes:\n${showFiles(generatedClasses)}")
        IO.deleteFilesEmptyDirs(generatedClasses)
        logger.debug(s"Restoring class files: \n${showFiles(movedClasses.keys)}")
        for ((orig, tmp) <- movedClasses) IO.move(tmp, orig)
      }
      logger.debug(s"Removing the temporary directory used for backing up class files: $tempDir")
      IO.delete(tempDir)
    }

    def move(c: File): File = {
      val target = File.createTempFile("sbt", ".class", tempDir)
      IO.move(c, target)
      target
    }
  }

  private final class DeleteClassFileManagerForJar(
      outputJar: File,
      outputJarContent: JarUtils.OutputJarContent
  ) extends XClassFileManager {
    override def delete(classes: Array[File]): Unit = {
      val relClasses = classes.map(c => JarUtils.ClassInJar.fromFile(c).toClassFilePath)
      outputJarContent.removeClasses(relClasses.toSet)
      JarUtils.removeFromJar(outputJar, relClasses)
    }
    override def generated(classes: Array[File]): Unit = ()
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
      outputJar: File,
      outputJarContent: JarUtils.OutputJarContent
  ) extends XClassFileManager {
    private val backedUpIndex = Some(outputJar).filter(_.exists()).map(JarUtils.stashIndex)

    override def delete(classesInJar: Array[File]): Unit = {
      val classes = classesInJar.map(c => JarUtils.ClassInJar.fromFile(c).toClassFilePath)
      JarUtils.removeFromJar(outputJar, classes)
      outputJarContent.removeClasses(classes.toSet)
    }

    override def generated(classes: Array[File]): Unit = ()

    override def complete(success: Boolean): Unit = {
      if (!success) {
        backedUpIndex.foreach(index => JarUtils.unstashIndex(outputJar, index))
      }
    }
  }
}
