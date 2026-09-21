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
import sbt.util.Logger
import sbt.internal.io.Retry

object PickleJar:
  private val pickleExtensions = List(".sig", ".tasty")

  // create an empty JAR file in case the subproject has no classes.
  def touch(path: Path): Unit =
    if !Files.exists(path) then
      Files.createDirectories(path.getParent)
      RootPath(path, writable = true).close() // create an empty jar

  def write(pickleOut: Path, knownProducts: java.util.Set[String], log: Logger): Unit =
    touch(pickleOut)
    if !knownProducts.isEmpty then
      val jar = pickleOut.toFile
      val unknown = IndexBasedZipFsOps.listEntries(jar).filter(isUnknown(_, knownProducts))
      if unknown.nonEmpty then
        log.debug(s"PickleJar.write: removing ${unknown.mkString(", ")}")
        // In place: on Windows the jar cannot be replaced while a compiler has it open.
        IndexBasedZipFsOps.removeEntries(jar, unknown)

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
