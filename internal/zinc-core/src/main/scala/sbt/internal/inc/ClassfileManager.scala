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

import collection.mutable
import xsbti.compile.{ DeleteImmediatelyManagerType, IncOptions, TransactionalManagerType }
import xsbti.compile.ClassFileManager

object ClassfileManager {

  private case class WrappedClassfileManager(internal: ClassFileManager, external: Option[ClassFileManager])
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

  def getClassfileManager(options: IncOptions): ClassFileManager = {
    val internal =
      if (options.classfileManagerType.isDefined)
        options.classfileManagerType.get match {
          case _: DeleteImmediatelyManagerType => deleteImmediately()
          case m: TransactionalManagerType     => transactional(m.backupDirectory, m.logger)()
        }
      else deleteImmediately()

    val external = Option(options.externalHooks())
      .flatMap(ext => Option(ext.externalClassFileManager))
      .collect {
        case manager: ClassFileManager => manager
      }

    WrappedClassfileManager(internal, external)
  }

  /** Constructs a minimal ClassfileManager implementation that immediately deletes class files when requested. */
  val deleteImmediately: () => ClassFileManager = () => new ClassFileManager {
    override def delete(classes: Array[File]): Unit = IO.deleteFilesEmptyDirs(classes)
    override def generated(classes: Array[File]): Unit = ()
    override def complete(success: Boolean): Unit = ()
  }

  @deprecated("Use overloaded variant that takes additional logger argument, instead.", "0.13.5")
  def transactional(tempDir0: File): () => ClassFileManager =
    transactional(tempDir0, sbt.util.Logger.Null)

  /** When compilation fails, this ClassfileManager restores class files to the way they were before compilation. */
  def transactional(tempDir0: File, logger: sbt.util.Logger): () => ClassFileManager = () => new ClassFileManager {
    val tempDir = tempDir0.getCanonicalFile
    IO.delete(tempDir)
    IO.createDirectory(tempDir)
    logger.debug(s"Created transactional ClassfileManager with tempDir = $tempDir")

    private[this] val generatedClasses = new mutable.HashSet[File]
    private[this] val movedClasses = new mutable.HashMap[File, File]

    private def showFiles(files: Iterable[File]): String = files.map(f => s"\t$f").mkString("\n")

    override def delete(classes: Array[File]): Unit = {
      logger.debug(s"About to delete class files:\n${showFiles(classes)}")
      val toBeBackedUp = classes.filter(c => c.exists && !movedClasses.contains(c) && !generatedClasses(c))
      logger.debug(s"We backup classs files:\n${showFiles(toBeBackedUp)}")
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
