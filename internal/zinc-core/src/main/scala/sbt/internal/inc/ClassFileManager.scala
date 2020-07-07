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
    val external = Option(options.externalHooks())
      .flatMap(
        ext =>
          ext.getExternalClassFileManager.toOption
            .map(cm => (new DelegatingClassFileManager(cm, converter): XClassFileManager))
      )
    xsbti.compile.WrappedClassFileManager.of(internal, external.toOptional)
  }

  private type XClassFileManager13 = {
    def delete(classes: Array[File]): Unit
    def generated(classes: Array[File]): Unit
  }

  /**
   * Workaround for Zinc 1.4.0 breaking changes.
   * This provides reflective fallback to Zinc 1.3.x XClassFileManager.
   */
  private final class DelegatingClassFileManager(
      underlying: XClassFileManager,
      converter: FileConverter
  ) extends XClassFileManager {

    import scala.language.reflectiveCalls
    override def delete(classes: Array[VirtualFile]): Unit =
      try {
        underlying.delete(classes)
      } catch {
        case _: AbstractMethodError =>
          underlying
            .asInstanceOf[XClassFileManager13]
            .delete(classes.map(vf => converter.toPath(vf).toFile))
      }

    override def generated(classes: Array[VirtualFile]): Unit =
      try {
        underlying.generated(classes)
      } catch {
        case _: AbstractMethodError =>
          underlying
            .asInstanceOf[XClassFileManager13]
            .generated(classes.map(vf => converter.toPath(vf).toFile))
      }

    override def complete(success: Boolean): Unit = underlying.complete(success)

    def containsMethod(x: AnyRef, name: String, params: java.lang.Class[_]*) =
      try {
        x.getClass.getMethod(name, params: _*)
        true
      } catch {
        case _: NoSuchMethodException => false
      }
  }

  private final class DeleteClassFileManager extends XClassFileManager {
    override def delete(classes: Array[VirtualFile]): Unit =
      IO.deleteFilesEmptyDirs(classes.toVector.map(toPath).map(_.toFile))
    override def generated(classes: Array[VirtualFile]): Unit = ()
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

    private[this] val generatedClasses = new mutable.HashSet[VirtualFile]
    private[this] val movedClasses = new mutable.HashMap[VirtualFile, Path]

    private def showFiles(files: Iterable[VirtualFile]): String =
      files.map(f => s"\t${f.id}").mkString("\n")

    override def delete(classes0: Array[VirtualFile]): Unit = {
      logger.debug(s"About to delete class files:\n${showFiles(classes0)}")
      val toBeBackedUp =
        classes0.toVector.filter(
          c => Files.exists(toPath(c)) && !movedClasses.contains(c) && !generatedClasses(c)
        )
      logger.debug(s"We backup class files:\n${showFiles(toBeBackedUp)}")
      for { c <- toBeBackedUp } {
        movedClasses.put(c, move(c))
      }
      IO.deleteFilesEmptyDirs(classes0.map(toPath).map(_.toFile))
    }

    override def generated(classes0: Array[VirtualFile]): Unit = {
      logger.debug(s"Registering generated classes:\n${showFiles(classes0)}")
      generatedClasses ++= classes0
      ()
    }

    override def complete(success: Boolean): Unit = {
      if (!success) {
        logger.debug("Rolling back changes to class files.")
        logger.debug(s"Removing generated classes:\n${showFiles(generatedClasses)}")
        IO.deleteFilesEmptyDirs(generatedClasses.toVector.map(toPath).map(_.toFile))
        logger.debug(s"Restoring class files: \n${showFiles(movedClasses.keys)}")
        for {
          (orig, tmp) <- movedClasses
        } {
          if (Files.exists(tmp)) {
            val origPath = toPath(orig)
            if (!Files.exists(origPath.getParent)) {
              Files.createDirectories(origPath.getParent)
            } // if
            Files.move(tmp, origPath)
          } // if
        }
      }
      logger.debug(s"Removing the temporary directory used for backing up class files: $tempDir")
      IO.delete(tempDir.toFile)
    }

    def move(c: VirtualFile): Path = {
      val target = Files.createTempFile(tempDir, "sbt", ".class")
      IO.move(toPath(c).toFile, target.toFile)
      target
    }
  }

  private final class DeleteClassFileManagerForJar(
      outputJar: Path,
      outputJarContent: JarUtils.OutputJarContent
  ) extends XClassFileManager {
    override def delete(classes0: Array[VirtualFile]): Unit = {
      val classes = classes0.toVector.map(toPath)
      val relClasses = classes.map(c => JarUtils.ClassInJar.fromPath(c).toClassFilePath.get)
      outputJarContent.removeClasses(relClasses.toSet)
      JarUtils.removeFromJar(outputJar, relClasses)
    }
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

    override def delete(classesInJar: Array[VirtualFile]): Unit = {
      val classes =
        classesInJar.toVector
          .map(toPath)
          .map(c => JarUtils.ClassInJar.fromPath(c).toClassFilePath.get)
      JarUtils.removeFromJar(outputJar, classes)
      outputJarContent.removeClasses(classes.toSet)
    }

    override def generated(classes: Array[VirtualFile]): Unit = ()

    override def complete(success: Boolean): Unit = {
      if (!success) {
        backedUpIndex.foreach(index => JarUtils.unstashIndex(outputJar, index))
      }
    }
  }
}
