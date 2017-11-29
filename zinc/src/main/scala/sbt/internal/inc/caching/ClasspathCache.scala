package sbt.internal.inc.caching

import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.attribute.{ BasicFileAttributes, FileTime }

import xsbti.compile.FileHash
import sbt.internal.inc.{ EmptyStamp, Stamper }

object ClasspathCache {
  // For more safety, store both the time and size
  private type JarMetadata = (FileTime, Long)
  private[this] val cacheMetadataJar = new ConcurrentHashMap[File, (JarMetadata, FileHash)]()
  private[this] final val emptyStampCode = EmptyStamp.hashCode()
  private def emptyFileHash(file: File) = FileHash.of(file, emptyStampCode)
  private def genFileHash(file: File, metadata: JarMetadata): FileHash = {
    val newHash = FileHash.of(file, Stamper.forHash(file).hashCode())
    cacheMetadataJar.put(file, (metadata, newHash))
    newHash
  }

  def hashClasspath(classpath: Seq[File]): Array[FileHash] = {
    // #433: Cache jars with their metadata to avoid recomputing hashes transitively in other projects
    def fromCacheOrHash(file: File): FileHash = {
      if (!file.exists()) emptyFileHash(file)
      else {
        // `readAttributes` needs to be guarded by `file.exists()`, otherwise it fails
        val attrs = Files.readAttributes(file.toPath, classOf[BasicFileAttributes])
        if (attrs.isDirectory) emptyFileHash(file)
        else {
          val currentMetadata = (attrs.lastModifiedTime(), attrs.size())
          Option(cacheMetadataJar.get(file)) match {
            case Some((metadata, hashHit)) if metadata == currentMetadata => hashHit
            case _                                                        => genFileHash(file, currentMetadata)
          }
        }
      }
    }

    classpath.toParArray.map(fromCacheOrHash).toArray
  }
}
