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
      output: Output
  ): XClassFileManager = {
    if (classFileManagerType.isPresent) {
      classFileManagerType.get match {
        case _: DeleteImmediatelyManagerType => deleteImmediately(output)
        case m: TransactionalManagerType =>
          transactional(output, m.backupDirectory, m.logger)
      }
    } else deleteImmediately(output)
  }

  def getClassFileManager(options: IncOptions, output: Output): XClassFileManager = {
    import sbt.internal.inc.JavaInterfaceUtil.{ EnrichOptional, EnrichOption }
    val internal = getDefaultClassFileManager(options.classfileManagerType, output)
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

  def deleteImmediatelyFromJar(outputJar: File): XClassFileManager =
    new DeleteClassFileManagerForJar(outputJar)

  def deleteImmediately(output: Output): XClassFileManager = {
    val outputJar = STJ.getOutputJar(output)
    outputJar.fold(deleteImmediately)(deleteImmediatelyFromJar)
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

  def transactionalForJar(outputJar: File): XClassFileManager = {
    new TransactionalClassFileManagerForJar(outputJar)
  }

  def transactional(output: Output, tempDir: File, logger: sbt.util.Logger): XClassFileManager = {
    val outputJar = STJ.getOutputJar(output)
    outputJar.fold(transactional(tempDir, logger))(transactionalForJar)
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

  private final class DeleteClassFileManagerForJar(outputJar: File) extends XClassFileManager {
    override def delete(classes: Array[File]): Unit = {
      val relClasses = classes.map(c => STJ.getRelClass(c.toString))
      STJ.removeFromJar(outputJar, relClasses)
    }
    override def generated(classes: Array[File]): Unit = ()
    override def complete(success: Boolean): Unit = ()
  }

  private final class TransactionalClassFileManagerForJar(outputJar: File)
      extends XClassFileManager {
    private val backedUpIndex = Some(outputJar).filter(_.exists()).map(STJ.stashIndex)

    override def delete(jaredClasses: Array[File]): Unit = {
      val classes = jaredClasses.map(s => STJ.getRelClass(s.toString))
      STJ.removeFromJar(outputJar, classes)
    }

    override def generated(classes: Array[File]): Unit = ()

    override def complete(success: Boolean): Unit = {
      if (!success) {
        backedUpIndex.foreach(index => STJ.unstashIndex(outputJar, index))
      }
    }
  }
}
