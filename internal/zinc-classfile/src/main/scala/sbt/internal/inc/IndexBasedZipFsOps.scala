package sbt.internal.inc

import java.io.OutputStream
import java.nio.file.Path

import sbt.internal.inc.zip.ZipCentralDir

import scala.collection.JavaConverters._

/**
 * The concrete implementation of [[sbt.internal.inc.IndexBasedZipOps]]
 * based on [[sbt.internal.inc.zip.ZipCentralDir]].
 */
object IndexBasedZipFsOps extends IndexBasedZipOps {
  override type CentralDir = ZipCentralDir
  override type Header = ZipCentralDir.Entry

  override protected def readCentralDir(path: Path): CentralDir = {
    new ZipCentralDir(path)
  }

  override protected def getCentralDirStart(centralDir: CentralDir): Long = {
    centralDir.getCentralDirStart
  }

  override protected def setCentralDirStart(centralDir: CentralDir, centralDirStart: Long): Unit = {
    centralDir.setCentralDirStart(centralDirStart)
  }

  override protected def getHeaders(centralDir: CentralDir): Seq[Header] = {
    centralDir.getHeaders.asScala
  }
  override protected def setHeaders(centralDir: CentralDir, headers: Seq[Header]): Unit = {
    centralDir.setHeaders(new java.util.ArrayList[Header](headers.asJava))
  }

  override protected def getFileName(header: Header): String = {
    header.getName
  }

  override protected def getFileOffset(header: Header): Long = {
    header.getEntryOffset
  }

  override protected def setFileOffset(header: Header, offset: Long): Unit = {
    header.setEntryOffset(offset)
  }

  override protected def getLastModifiedTime(header: Header): Long = {
    header.getLastModifiedTime
  }

  override protected def writeCentralDir(centralDir: CentralDir,
                                         outputStream: OutputStream): Unit = {
    centralDir.dump(outputStream)
  }
}
