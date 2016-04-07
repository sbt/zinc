package sbt
package internal
package inc

import sbt.io.IO
import sbt.io.Path._

import java.io.File
import collection.mutable
import xsbti.compile.{ IncOptions, DeleteImmediatelyManagerType, Output, SingleOutput, MultipleOutput, TransactionalManagerType }

/**
 * During an incremental compilation run, a ClassfileManager backs up classfiles, and
 * can restore them in case of failure.
 *
 * A ClassfileManager can be used only once.
 */
trait ClassfileManager {
  /**
   * Called before any changes to classfiles have been made.
   */
  def begin(): Unit

  /**
   * Called if changes to classfiles in the monitored location were successful.
   */
  def commit(): Unit

  /**
   * Called if changes to classfiles in the monitored location were unsuccessful and should
   * be rolled back.
   */
  def rollback(): Unit
}

object ClassfileManager {

  def manageClassfiles[T](output: Output, options: IncOptions)(run: => T): T =
    {
      val cfm = ClassfileManager.getClassfileManager(output, options)
      cfm.begin()
      val result =
        try {
          run
        } catch {
          case e: Exception =>
            cfm.rollback()
            throw e
        }
      cfm.commit()
      result
    }

  private def getClassfileManager(output: Output, options: IncOptions): ClassfileManager =
    if (options.classfileManagerType.isDefined)
      options.classfileManagerType.get match {
        case _: DeleteImmediatelyManagerType => Noop
        case m: TransactionalManagerType     => Transactional(output, m.backupDirectory, m.logger)
      }
    else Noop

  /** Constructs a noop ClassfileManager which does not back up classfiles before execution. */
  private object Noop extends ClassfileManager {
    def begin(): Unit = ()
    def commit(): Unit = ()
    def rollback(): Unit = ()
  }

  /**
   * When compilation fails, this ClassfileManager restores classfiles to the way they were
   * before compilation.
   */
  private case class Transactional(output: Output, tempDir0: File, logger: sbt.util.Logger) extends ClassfileManager {
    val tempDir = tempDir0.getCanonicalFile

    // Flatten to a list to guard against non-determinism.
    private val outputDirs: List[(File, File)] = {
      val items = output match {
        case single: SingleOutput  => Seq(single.outputDirectory)
        case multi: MultipleOutput => multi.outputGroups.map(_.outputDirectory).toSeq
      }
      items.zipWithIndex.map {
        case (outputDir, idx) => (outputDir, tempDir / idx.toString)
      }.toList
    }

    def begin(): Unit = {
      logger.debug(s"Beginning ClassfileManager transaction with tempDir $tempDir")
      IO.delete(tempDir)
      outputDirs.foreach {
        case (outputDir, tempOutputDir) =>
          IO.copyDirectory(outputDir, tempOutputDir, preserveLastModified = true)
      }
    }

    def commit(): Unit = {
      try {
        logger.debug("Committing changes to classfiles.")
        IO.delete(tempDir)
      } catch {
        case e: Throwable =>
          logger.debug(s"Failed to clean up temporary directory $tempDir: $e")
      }
    }

    def rollback(): Unit = {
      try {
        logger.debug("Rolling back changes to classfiles.")
        outputDirs.foreach {
          case (outputDir, tempOutputDir) =>
            IO.delete(outputDir)
            IO.copyDirectory(tempOutputDir, outputDir, preserveLastModified = true)
        }
      } catch {
        case e: Throwable =>
          logger.error(s"Failed to rollback previous classfiles from $tempDir: $e")
      }
    }
  }
}
