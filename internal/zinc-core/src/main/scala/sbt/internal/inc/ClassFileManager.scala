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
  ClassFileManager,
  ClassFileManagerType,
  DeleteImmediatelyManagerType,
  IncOptions,
  TransactionalManagerType
}

object ClassFileManager {

  /**
   * Defines a classfile manager that composes the operation of two classfile manager,
   * one being the internal classfile manager (the one used by the compiler) and the
   * other one being the external classfile manager (a customizable, build tool-defined
   * class file manager to control which class files should be notified/removed/generated
   * aside from the ones covered by the internal classfile manager).
   *
   * @param internal Compiler classfile manager.
   * @param external Build tool (or external) classfile manager the complements the internal one.
   */
  private case class WrappedClassFileManager(internal: ClassFileManager,
                                             external: Option[ClassFileManager])
      extends ClassFileManager {

    override def delete(classes: Array[File]): Unit = {
      external.foreach(_.delete(classes))
      internal.delete(classes)
    }

    override def complete(success: Boolean): Unit = {
      external.foreach(_.complete(success))
      internal.complete(success)
    }

    override def generated(classes: Array[File]): Unit = {
      external.foreach(_.generated(classes))
      internal.generated(classes)
    }
  }

  def getDefaultClassFileManager(
      classFileManagerType: Optional[ClassFileManagerType]): ClassFileManager = {
    if (classFileManagerType.isPresent) {
      classFileManagerType.get match {
        case _: DeleteImmediatelyManagerType => new DeleteClassFileManager
        case m: TransactionalManagerType     => transactional(m.backupDirectory, m.logger)
      }
    } else new DeleteClassFileManager
  }

  def getClassFileManager(options: IncOptions): ClassFileManager = {
    import sbt.internal.inc.JavaInterfaceUtil.EnrichOptional
    val internal = getDefaultClassFileManager(options.classfileManagerType)
    val external = Option(options.externalHooks())
      .flatMap(ext => ext.externalClassFileManager.toOption)
    WrappedClassFileManager(internal, external)
  }

  private final class DeleteClassFileManager extends ClassFileManager {
    override def delete(classes: Array[File]): Unit = IO.deleteFilesEmptyDirs(classes)
    override def generated(classes: Array[File]): Unit = ()
    override def complete(success: Boolean): Unit = ()
  }

  /**
   * Constructs a minimal [[ClassFileManager]] implementation that immediately deletes
   * class files when they are requested. This is the default implementation of the class
   * file manager by the Scala incremental compiler if no class file manager is specified.
   */
  def deleteImmediately: ClassFileManager = new DeleteClassFileManager

  /**
   * Constructs a transactional [[ClassFileManager]] implementation that restores class
   * files to the way they were before compilation if there is an error. Otherwise, it
   * keeps the successfully generated class files from the new compilation.
   *
   * This is the default class file manager used by sbt, and makes sense in a lot of scenarios.
   */
  def transactional(tempDir0: File, logger: sbt.util.Logger): ClassFileManager =
    new TransactionalClassFileManager(tempDir0, logger)

  private final class TransactionalClassFileManager(tempDir0: File, logger: sbt.util.Logger)
      extends ClassFileManager {
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
}
