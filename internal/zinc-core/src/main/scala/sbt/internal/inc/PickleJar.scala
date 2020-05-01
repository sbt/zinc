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

import java.io.{ Closeable, BufferedOutputStream, FileOutputStream }
import java.nio.file.{ Files, FileSystems, FileVisitResult, Path, SimpleFileVisitor }
import java.nio.file.attribute.{ BasicFileAttributes, FileTime }
import java.time.Instant
import java.util.Arrays
import java.util.zip.{ CRC32, Deflater, ZipEntry, ZipOutputStream }

import sbt.util.Logger
import xsbti.compile.PickleData
import scala.collection.JavaConverters._
import scala.collection.mutable

object PickleJar {
  def write(
      pickleOut: Path,
      data: Iterable[PickleData],
      knownProducts: collection.Set[String],
      log: Logger
  ): Path = {
    // def trace(msg: String) = log.trace(() => new Exception(msg))
    var pj: RootJarPath = null
    val writtenPickles = new java.util.IdentityHashMap[AnyRef, String]()
    val writtenSyms = new mutable.HashSet[String]
    val sigWriter = FileWriter(pickleOut, None)

    def writeSigFile(pickle: PickleData): Unit = {
      val fqcn = pickle.fqcn()
      // Reference to original scalac PickleBuffer (as AnyRef to avoid version dependence).
      // For some reason, these might be duplicated.
      val orig = pickle.underlying()
      if (!writtenPickles.containsKey(orig)) {
        if (writtenSyms.contains(fqcn))
          log.warn(s"Found duplicate fqcn $fqcn while writing pickles!")
        val elems: Iterable[String] = pickle.path.asScala.map(_.toString)
        assert(elems.head == "__ROOT__")
        val relativePath = elems.tail.mkString("/")
        sigWriter.writeFile(relativePath, Arrays.copyOf(pickle.data, pickle.writeIndex))
        writtenPickles.put(pickle.underlying, fqcn)
        ()
      } else ()
    }

    try {
      for { pickle <- data } {
        writeSigFile(pickle)
      }
    } finally {
      sigWriter.close()
    }
    try {
      pj = RootJarPath(pickleOut)
      val knownPaths = knownProducts
      Files.walkFileTree(
        pj.root,
        new SimpleFileVisitor[Path] {
          override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
            val ps = path.toString
            if (ps.endsWith(".sig")) {
              // "/foo/bar/wiz.sig" -> "foo/bar/wiz.class"
              val i0 = if (ps.startsWith("/")) 1 else 0
              val cp = ps.substring(i0, ps.length - 3) + "class"
              if (!knownPaths.contains(cp)) {
                // log.info(s"deleting sig for removed class $path")
                // trace(s"Removed $cp from picklejar")
                Files.delete(path)
              }

            }
            FileVisitResult.CONTINUE
          }
        }
      )
    } finally {
      if (pj ne null) {
        pj.close()
      }
    }
    Files.setLastModifiedTime(pickleOut, FileTime.from(Instant.now()))
  }

  sealed trait FileWriter {
    def writeFile(relativePath: String, bytes: Array[Byte]): Unit
    def close(): Unit
  }
  object FileWriter {
    def apply(file: Path, jarManifestMainClass: Option[String]): FileWriter = {
      if (file.getFileName.toString.endsWith(".jar")) {
        val jarCompressionLevel: Int = Deflater.DEFAULT_COMPRESSION
        new JarEntryWriter(
          file,
          jarManifestMainClass,
          jarCompressionLevel
        )
      } else {
        throw new IllegalStateException(
          s"don't know how to handle an output of $file [${file.getClass}]"
        )
      }
    }
  }

  private final class JarEntryWriter(
      file: Path,
      mainClass: Option[String],
      compressionLevel: Int
  ) extends FileWriter {
    //keep these imports local - avoid confusion with scala naming
    import java.util.jar.Attributes.Name.{ MANIFEST_VERSION, MAIN_CLASS }
    import java.util.jar.{ JarOutputStream, Manifest }

    val storeOnly = compressionLevel == Deflater.NO_COMPRESSION

    def createJarOutputStream(file: Path, manifest: Manifest): JarOutputStream =
      new JarOutputStream(
        new BufferedOutputStream(new FileOutputStream(file.toFile), 64000),
        manifest
      )

    lazy val jarWriter: JarOutputStream = {
      import scala.util.Properties._
      val manifest = new Manifest
      val attrs = manifest.getMainAttributes
      attrs.put(MANIFEST_VERSION, "1.0")
      attrs.put(ScalaCompilerVersion, versionNumberString)
      mainClass.foreach(c => attrs.put(MAIN_CLASS, c))
      // plugins.foreach(_.augmentManifest(file, manifest))
      prepJarFile()
      val jar = createJarOutputStream(file, manifest)
      jar.setLevel(compressionLevel)
      if (storeOnly) jar.setMethod(ZipOutputStream.STORED)
      jar
    }

    lazy val crc = new CRC32

    def prepJarFile(): Unit = {
      if (!Files.exists(file.getParent)) {
        Files.createDirectories(file.getParent)
      }
      if (!Files.exists(file)) {
        Files.createFile(file)
      }
      ()
    }

    override def writeFile(relativePath: String, bytes: Array[Byte]): Unit = this.synchronized {
      val entry = new ZipEntry(relativePath)
      if (storeOnly) {
        // When using compression method `STORED`, the ZIP spec requires the CRC and compressed/
        // uncompressed sizes to be written before the data. The JarOutputStream could compute the
        // values while writing the data, but not patch them into the stream after the fact. So we
        // need to pre-compute them here. The compressed size is taken from size.
        // https://stackoverflow.com/questions/1206970/how-to-create-uncompressed-zip-archive-in-java/5868403
        // With compression method `DEFLATED` JarOutputStream computes and sets the values.
        entry.setSize(bytes.length.toLong)
        crc.reset()
        crc.update(bytes)
        entry.setCrc(crc.getValue)
      }
      jarWriter.putNextEntry(entry)
      try jarWriter.write(bytes, 0, bytes.length)
      finally jarWriter.flush()
    }

    override def close(): Unit = this.synchronized(jarWriter.close())
  }

  // RootJarPath is identical to s.reflect.io.RootPath, except that it doesn't delete pre-existing jars.
  abstract class RootJarPath extends Closeable {
    def root: Path
  }

  object RootJarPath {
    def apply(path: Path): RootJarPath = {
      assert(path.getFileName.toString.endsWith(".jar"))
      import java.net.URI
      val zipFile = URI.create("jar:file:" + path.toUri.getPath)
      val env = new java.util.HashMap[String, String]()
      val zipfs = FileSystems.newFileSystem(zipFile, env)
      if (!Files.exists(path)) {
        env.put("create", "true")
      }
      new RootJarPath {
        def root = zipfs.getRootDirectories.iterator().next()
        def close(): Unit = {
          zipfs.close()
        }
      }
    }
  }
}
