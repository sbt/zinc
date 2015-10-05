package sbt
package internal
package inc

import sbt.io.IO
import java.io.File
import collection.mutable

import xsbti.compile.ClassfileManager

object DefaultClassfileManager {
  /** Constructs a minimal ClassfileManager implementation that immediately deletes class files when requested. */
  val deleteImmediately: () => ClassfileManager = () => new ClassfileManager {
    def delete(classes: Array[File]): Unit = IO.deleteFilesEmptyDirs(classes)
    def generated(classes: Array[File]): Unit = ()
    def complete(success: Boolean): Unit = ()
  }
  @deprecated("Use overloaded variant that takes additional logger argument, instead.", "0.13.5")
  def transactional(tempDir0: File): () => ClassfileManager =
    transactional(tempDir0, sbt.util.Logger.Null)
  /** When compilation fails, this ClassfileManager restores class files to the way they were before compilation.*/
  def transactional(tempDir0: File, logger: sbt.util.Logger): () => ClassfileManager = () => new ClassfileManager {
    val tempDir = tempDir0.getCanonicalFile
    IO.delete(tempDir)
    IO.createDirectory(tempDir)
    logger.debug(s"Created transactional ClassfileManager with tempDir = $tempDir")

    private[this] val generatedClasses = new mutable.HashSet[File]
    private[this] val movedClasses = new mutable.HashMap[File, File]

    private def showFiles(files: Iterable[File]): String = files.map(f => s"\t$f").mkString("\n")
    def delete(classes: Array[File]): Unit = {
      logger.debug(s"About to delete class files:\n${showFiles(classes)}")
      val toBeBackedUp = classes.filter(c => c.exists && !movedClasses.contains(c) && !generatedClasses(c))
      logger.debug(s"We backup classs files:\n${showFiles(toBeBackedUp)}")
      for (c <- toBeBackedUp) {
        movedClasses.put(c, move(c))
      }
      IO.deleteFilesEmptyDirs(classes)
    }
    def generated(classes: Array[File]): Unit = {
      logger.debug(s"Registering generated classes:\n${showFiles(classes)}")
      generatedClasses ++= classes
      ()
    }
    def complete(success: Boolean): Unit = {
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

    def move(c: File): File =
      {
        val target = File.createTempFile("sbt", ".class", tempDir)
        IO.move(c, target)
        target
      }
  }
}
