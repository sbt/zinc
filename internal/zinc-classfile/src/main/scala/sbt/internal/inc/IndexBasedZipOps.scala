package sbt.internal.inc

import java.nio.channels.{ FileChannel, Channels, ReadableByteChannel }
import java.io._
import java.nio.file.{ Files, Path }
import java.util.UUID
import java.util.zip.{ Deflater, ZipOutputStream, ZipEntry }

import sbt.io.{ IO, Using }

abstract class IndexBasedZipOps extends CreateZip {

  class CachedStampReader {
    private var cachedNameToTimestamp: Map[String, Long] = _

    def readStamp(jar: File, cls: String): Long = {
      if (cachedNameToTimestamp == null) {
        cachedNameToTimestamp = initializeCache(jar)
      }
      cachedNameToTimestamp.getOrElse(cls, 0)
    }

    private def initializeCache(jar: File): Map[String, Long] = {
      if (jar.exists()) {
        val centralDir = readCentralDir(jar.toPath)
        val headers = getHeaders(centralDir)
        headers.map(header => getFileName(header) -> getLastModifiedTime(header))(
          collection.breakOut)
      } else {
        Map.empty
      }
    }
  }

  def removeEntries(jarFile: File, classes: Iterable[String]): Unit = {
    removeEntries(jarFile.toPath, classes.toSet)
  }

  def mergeArchives(into: File, from: File): Unit = {
    mergeArchives(into.toPath, from.toPath)
  }

  def includeInJar(jar: File, files: Seq[(File, String)]): Unit = {
    if (jar.exists()) {
      val tempZip = jar.toPath.resolveSibling(s"${UUID.randomUUID()}.jar").toFile
      createZip(tempZip, files)
      mergeArchives(jar, tempZip)
    } else {
      createZip(jar, files)
    }
  }

  def readCentralDir(file: File): CentralDir = {
    readCentralDir(file.toPath)
  }

  def writeCentralDir(file: File, centralDir: CentralDir): Unit = {
    writeCentralDir(file.toPath, centralDir)
  }

  type CentralDir
  type Header

  private def writeCentralDir(path: Path, newCentralDir: CentralDir): Unit = {
    val currentCentralDir = readCentralDir(path)
    val currentCentralDirStart = truncateCentralDir(currentCentralDir, path)
    finalizeZip(newCentralDir, path, currentCentralDirStart)
  }

  private def removeEntries(path: Path, toRemove: Set[String]): Unit = {
    val centralDir = readCentralDir(path)
    removeEntriesFromCentralDir(centralDir, toRemove)
    val writeOffset = truncateCentralDir(centralDir, path)
    finalizeZip(centralDir, path, writeOffset)
  }

  private def removeEntriesFromCentralDir(centralDir: CentralDir, toRemove: Set[String]): Unit = {
    val headers = getHeaders(centralDir)
    val clearedHeaders = headers.filterNot(header => toRemove.contains(getFileName(header)))
    setHeaders(centralDir, clearedHeaders)
  }

  private def mergeArchives(target: Path, source: Path): Unit = {
    val targetCentralDir = readCentralDir(target)
    val sourceCentralDir = readCentralDir(source)

    // "source" will start where "target" ends
    val sourceStart = truncateCentralDir(targetCentralDir, target)
    // "source" data (files) is as long as from its beginning till the start of central dir
    val sourceLength = getCentralDirStart(sourceCentralDir)

    transferAll(source, target, startPos = sourceStart, bytesToTransfer = sourceLength)

    val mergedHeaders = mergeHeaders(targetCentralDir, sourceCentralDir, sourceStart)
    setHeaders(targetCentralDir, mergedHeaders)

    val centralDirStart = sourceStart + sourceLength
    finalizeZip(targetCentralDir, target, centralDirStart)

    Files.delete(source)
  }

  private def mergeHeaders(
      targetCentralDir: CentralDir,
      sourceCentralDir: CentralDir,
      sourceStart: Long
  ): Seq[Header] = {
    val sourceHeaders = getHeaders(sourceCentralDir)
    sourceHeaders.foreach { header =>
      // potentially offsets should be updated for each header
      // not only in central directory but a valid zip tool
      // should not rely on that unless the file is corrupted
      val currentOffset = getFileOffset(header)
      val newOffset = currentOffset + sourceStart
      setFileOffset(header, newOffset)
    }

    // override files from target with files from source
    val sourceNames = sourceHeaders.map(getFileName).toSet
    val targetHeaders =
      getHeaders(targetCentralDir).filterNot(h => sourceNames.contains(getFileName(h)))

    targetHeaders ++ sourceHeaders
  }

  private def truncateCentralDir(centralDir: CentralDir, path: Path): Long = {
    val sizeAfterTruncate = getCentralDirStart(centralDir)
    new FileOutputStream(path.toFile, true).getChannel
      .truncate(sizeAfterTruncate)
      .close()
    sizeAfterTruncate
  }

  private def finalizeZip(
      centralDir: CentralDir,
      path: Path,
      centralDirStart: Long
  ): Unit = {
    setCentralDirStart(centralDir, centralDirStart)
    val fileOutputStream = new FileOutputStream(path.toFile, /*append =*/ true)
    fileOutputStream.getChannel.position(centralDirStart)
    val outputStream = new BufferedOutputStream(fileOutputStream)
    writeCentralDir(centralDir, outputStream)
    outputStream.close()
  }

  private def transferAll(
      source: Path,
      target: Path,
      startPos: Long,
      bytesToTransfer: Long
  ): Unit = {
    val sourceFile = openFileForReading(source)
    val targetFile = openFileForWriting(target)
    var remaining = bytesToTransfer
    var offset = startPos
    while (remaining > 0) {
      val transferred =
        targetFile.transferFrom(sourceFile, /*position =*/ offset, /*count = */ remaining)
      offset += transferred
      remaining -= transferred
    }
    sourceFile.close()
    targetFile.close()
  }

  private def openFileForReading(path: Path): ReadableByteChannel = {
    Channels.newChannel(new BufferedInputStream(Files.newInputStream(path)))
  }

  private def openFileForWriting(path: Path): FileChannel = {
    new FileOutputStream(path.toFile, /*append = */ true).getChannel
  }

  protected def readCentralDir(path: Path): CentralDir

  protected def getCentralDirStart(centralDir: CentralDir): Long
  protected def setCentralDirStart(centralDir: CentralDir, centralDirStart: Long): Unit

  protected def getHeaders(centralDir: CentralDir): Seq[Header]
  protected def setHeaders(centralDir: CentralDir, headers: Seq[Header]): Unit

  protected def getFileName(header: Header): String

  protected def getFileOffset(header: Header): Long
  protected def setFileOffset(header: Header, offset: Long): Unit
  protected def getLastModifiedTime(header: Header): Long

  protected def writeCentralDir(centralDir: CentralDir, outputStream: OutputStream): Unit

}

// Adapted from sbt.io.IO.zip - disabled compression and simplified
sealed trait CreateZip {

  def createZip(target: File, files: Seq[(File, String)]): Unit = {
    IO.createDirectory(target.getParentFile)
    withZipOutput(target) { output =>
      writeZip(files, output)
    }
  }

  private def withZipOutput(file: File)(f: ZipOutputStream => Unit): Unit = {
    Using.fileOutputStream()(file) { fileOut =>
      val zipOut = new ZipOutputStream(fileOut)
      zipOut.setMethod(ZipOutputStream.DEFLATED)
      zipOut.setLevel(Deflater.NO_COMPRESSION)
      try { f(zipOut) } finally { zipOut.close() }
    }
  }

  private def writeZip(files: Seq[(File, String)], output: ZipOutputStream): Unit = {
    val now = System.currentTimeMillis()

    def makeFileEntry(file: File, name: String): ZipEntry = {
      val entry = new ZipEntry(name)
      entry.setTime(now)
      entry
    }

    def addFileEntry(file: File, name: String): Unit = {
      output.putNextEntry(makeFileEntry(file, name))
      IO.transfer(file, output)
      output.closeEntry()
    }

    files.foreach { case (file, name) => addFileEntry(file, normalizeName(name)) }
  }

  private def normalizeName(name: String): String = {
    val sep = File.separatorChar
    if (sep == '/') name else name.replace(sep, '/')
  }

}
