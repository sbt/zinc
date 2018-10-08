package sbt.internal.inc

import java.nio.channels.{ FileChannel, Channels, ReadableByteChannel }
import java.io._
import java.nio.file.{ Files, Path }
import java.util.UUID
import java.util.zip.{ Deflater, ZipOutputStream, ZipEntry }

import sbt.io.{ IO, Using }

/**
 * Provides efficient implementation of operations on zip files * that are
 * used for implementation of the Straight to Jar feature.
 *
 * The implementation is based on index (aka central directory) that is
 * located at the end of the zip file and contains among others the name/path
 * and offset where the actual data of stored file is located. Reading zips
 * should always be done based on that index, which means that it is often enough
 * to manipulate this index without rewriting the other part of the file.
 * This class heavily relies on this fact.
 *
 * This class abstracts over the actual operations on index i.e. reading, manipulating
 * and storing it making it easy to replace.
 */
abstract class IndexBasedZipOps extends CreateZip {

  /**
   * Reads timestamps of zip entries. On the first access to a given zip
   * it reads the timestamps once and keeps them cached for future lookups.
   *
   * It only supports reading stamps from a single zip. The zip passed as
   * an argument is only used to initialize the cache and is later ignored.
   * This is enough as stamps are only read from the output jar.
   */
  final class CachedStamps(zip: File) {
    private val cachedNameToTimestamp: Map[String, Long] = initializeCache(zip)

    def getStamp(entry: String): Long = {
      cachedNameToTimestamp.getOrElse(entry, 0)
    }

    private def initializeCache(zipFile: File): Map[String, Long] = {
      if (zipFile.exists()) {
        val centralDir = readCentralDir(zipFile.toPath)
        val headers = getHeaders(centralDir)
        headers.map(header => getFileName(header) -> getLastModifiedTime(header))(
          collection.breakOut)
      } else {
        Map.empty
      }
    }
  }

  /**
   * Removes specified entries from given zip file by replacing current index
   * with a version without those entries.
   * @param zipFile the zip file to remove entries from
   * @param entries paths to files inside the jar e.g. sbt/internal/inc/IndexBasedZipOps.class
   */
  def removeEntries(zipFile: File, entries: Iterable[String]): Unit = {
    removeEntries(zipFile.toPath, entries.toSet)
  }

  /**
   * Merges two zip files. It works by appending contents of `from`
   * to `into`. Indices are combined, in case of duplicates, the
   * final entries that are used are from `from`.
   * The final merged zip is available under `into` path, and `from`
   * is deleted.
   *
   * @param into the target zip file to merge to
   * @param from the source zip file that is added/merged to `into`
   */
  def mergeArchives(into: File, from: File): Unit = {
    mergeArchives(into.toPath, from.toPath)
  }

  /**
   * Adds `files` (plain files) to the specified zip file. Implemented by creating
   * a new zip with the plain files. If `zipFile` already exists, the archives will
   * be merged.
   * Plain files are not removed after this operation.
   *
   * @param zipFile A zip file to add files to
   * @param files a sequence of tuples with actual file to include and the path in
   *             the zip where it should be put.
   */
  def includeInArchive(zipFile: File, files: Seq[(File, String)]): Unit = {
    if (zipFile.exists()) {
      val tempZip = zipFile.toPath.resolveSibling(s"${UUID.randomUUID()}.jar").toFile
      createZip(tempZip, files)
      mergeArchives(zipFile, tempZip)
    } else {
      createZip(zipFile, files)
    }
  }

  /**
   * Reads the current index from given zip file
   *
   * @param zipFile path to the zip file
   * @return current index
   */
  def readCentralDir(zipFile: File): CentralDir = {
    readCentralDir(zipFile.toPath)
  }

  /**
   * Replaces index inside the zip file.
   *
   * @param zipFile the zip file that should have the index updated
   * @param centralDir the index to be stored in the file
   */
  def writeCentralDir(zipFile: File, centralDir: CentralDir): Unit = {
    writeCentralDir(zipFile.toPath, centralDir)
  }

  def listEntries(zipFile: File): Seq[String] = {
    val centralDir = readCentralDir(zipFile)
    val headers = getHeaders(centralDir)
    headers.map(getFileName)
  }

  /**
   * Represents the central directory (index) of a zip file. It must contain the start offset
   * (where it is located in the zip file) and list of headers
   */
  type CentralDir

  /**
   * Represents a header of a zip entry located inside the central directory. It has to contain
   * the timestamp, name/path and offset to the actual data in zip file.
   */
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

    def makeFileEntry(name: String): ZipEntry = {
      val entry = new ZipEntry(name)
      entry.setTime(now)
      entry
    }

    def addFileEntry(file: File, name: String): Unit = {
      output.putNextEntry(makeFileEntry(name))
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
