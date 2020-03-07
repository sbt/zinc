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

import java.io.{ Closeable, OutputStream }
import java.nio.file.{
  Files,
  FileSystems,
  FileVisitResult,
  Path,
  StandardOpenOption,
  SimpleFileVisitor
}
import java.nio.file.attribute.{ BasicFileAttributes, FileTime }
import java.time.Instant

import sbt.util.Logger
import xsbti.compile.PickleData
import scala.collection.JavaConverters._
import scala.collection.mutable

object PickleJar {
  def write(
      pickleOut: Path,
      data: Iterable[PickleData],
      knownClasses: collection.Set[String],
      log: Logger
  ): Path = {

    // def trace(msg: String) = log.trace(() => new Exception(msg))

    var pj: RootJarPath = null
    try {
      pj = RootJarPath(pickleOut)
      Files.createDirectories(pj.root)
      val knownPaths = knownClasses

      val writtenPickles = new java.util.IdentityHashMap[AnyRef, String]()
      val writtenSyms = new mutable.HashSet[String]
      for { pickle <- data } {
        val fqcn = pickle.fqcn()
        // Reference to original scalac PickleBuffer (as AnyRef to avoid version dependence).
        // For some reason, these might be duplicated.
        val orig = pickle.underlying()
        if (!writtenPickles.containsKey(orig)) {
          if (writtenSyms.contains(fqcn))
            log.warn(s"Found duplicate fqcn $fqcn while writing pickles!")
          val elems: Iterable[String] = pickle.path.asScala.map(_.toString)
          assert(elems.head == "__ROOT__")
          val primary = elems.tail.foldLeft(pj.root)(_.resolve(_))
          Files.createDirectories(primary.getParent)
          var out: OutputStream = null
          try {
            out = Files.newOutputStream(
              primary,
              StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING
            )
            out.write(pickle.data, 0, pickle.writeIndex)
            writtenSyms += fqcn
            // trace(s"Added $fqcn to pickle jar")
          } finally {
            if (out ne null)
              out.close()
          }
          writtenPickles.put(pickle.underlying, fqcn)
        }
      }

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
                // log.info(s"Deleting sig for removed class $path")
                // trace(s"Removed $cp from picklejar")
                Files.delete(path)
              }

            }
            FileVisitResult.CONTINUE
          }
        }
      )

    } finally {
      if (pj ne null)
        pj.close()
    }
    Files.setLastModifiedTime(pickleOut, FileTime.from(Instant.now()))
  }
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
    if (!Files.exists(path.getParent))
      Files.createDirectories(path.getParent)
    if (!Files.exists(path))
      env.put("create", "true")
    val zipfs = FileSystems.newFileSystem(zipFile, env)
    new RootJarPath {
      def root = zipfs.getRootDirectories.iterator().next()
      def close(): Unit = {
        zipfs.close()
      }
    }
  }
}
