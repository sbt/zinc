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

import java.nio.file.{ FileVisitResult, Files, Path, SimpleFileVisitor }
import java.nio.file.attribute.BasicFileAttributes
import sbt.util.Logger
import scala.reflect.io.RootPath
import sbt.internal.io.Retry

object PickleJar {
  // create an empty JAR file in case the subproject has no classes.
  def touch(path: Path): Unit = {
    if (!Files.exists(path)) {
      Files.createDirectories(path.getParent)
      RootPath(path, writable = true).close() // create an empty jar
    }
  }

  def write(pickleOut: Path, knownProducts: java.util.Set[String], log: Logger): Unit = {
    touch(pickleOut)
    if (!knownProducts.isEmpty) {
      val pj = RootPath(pickleOut, writable = false) // so it doesn't delete the file
      try Files.walkFileTree(pj.root, deleteUnknowns(knownProducts, log))
      finally Retry(pj.close())
    }
    ()
  }

  def deleteUnknowns(knownProducts: java.util.Set[String], log: Logger) =
    new SimpleFileVisitor[Path] {
      override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val ps = path.toString
        if (ps.endsWith(".sig")) {
          // "/foo/bar/wiz.sig" -> "foo/bar/wiz.class"
          if (!knownProducts.contains(ps.stripPrefix("/").stripSuffix(".sig") + ".class")) {
            log.debug(s"PickleJar.deleteUnknowns: visitFile deleting $ps")
            // retry to work around C:\Users\RUNNER~1\AppData\Local\Temp\sbt_f3e67bfa\dep\target\early\output.jar:
            // The process cannot access the file because it is being used by another process.
            Retry(Files.delete(path))
          }
        }
        FileVisitResult.CONTINUE
      }
    }
}
