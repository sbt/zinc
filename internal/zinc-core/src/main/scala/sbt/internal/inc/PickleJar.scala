/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc

import java.nio.file.{ FileVisitResult, Files, Path, SimpleFileVisitor }
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarFile
import sbt.util.Logger
import sbt.internal.io.Retry

object PickleJar:
  private val pickleExtensions = List(".sig", ".tasty")

  // create an empty JAR file in case the subproject has no classes.
  def touch(path: Path): Unit =
    if !Files.exists(path) then
      Files.createDirectories(path.getParent)
      RootPath(path, writable = true).close() // create an empty jar

  /**
   * Merges the pickles of `updates` into `pickleOut` and, given `knownProducts`, drops those of
   * classes missing from it, in one [[SortedJar.write]], so that equal pickles give equal bytes.
   * The manifest that scalac writes into a pickle jar is left out: a jar without pickles is empty.
   */
  def write(
      pickleOut: Path,
      updates: Option[Path],
      knownProducts: Option[java.util.Set[String]],
      keep: (Path, Long) => Boolean,
      log: Logger
  ): Unit =
    val merged = updates.filter(Files.exists(_))
    if merged.isEmpty && knownProducts.isEmpty then touch(pickleOut)
    else
      merged.foreach(u => log.debug(s"merging $u into $pickleOut"))
      val before = SortedJar.entries(pickleOut)
      val added = SortedJar.reuse(before, merged.toVector.flatMap(SortedJar.entries))
      val addedNames = added.iterator.map(_.name).toSet
      val (dropped, after) = (before.filterNot(e => addedNames(e.name)) ++ added)
        .filterNot(_.name == JarFile.MANIFEST_NAME)
        .partition(e => knownProducts.exists(isUnknown(e.name, _)))
      if dropped.nonEmpty then
        log.debug(s"PickleJar.write: removing ${dropped.map(_.name).mkString(", ")}")
      SortedJar.write(pickleOut, before, after, keep)
      merged.foreach(Files.delete)
  end write

  // "foo/bar/wiz.sig" -> "foo/bar/wiz.class"
  private def isUnknown(entry: String, knownProducts: java.util.Set[String]): Boolean =
    pickleExtensions.find(entry.endsWith).exists { ext =>
      !knownProducts.contains(entry.stripSuffix(ext) + ".class")
    }

  @deprecated("Fails silently on Windows while the jar is open elsewhere. Use write.", "2.0.0")
  def deleteUnknowns(knownProducts: java.util.Set[String], log: Logger) =
    new SimpleFileVisitor[Path]:
      override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult =
        val ps = path.toString
        if isUnknown(ps.stripPrefix("/"), knownProducts) then
          log.debug(s"PickleJar.deleteUnknowns: visitFile deleting $ps")
          Retry(Files.delete(path))
        FileVisitResult.CONTINUE
end PickleJar
