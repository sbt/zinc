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

import scala.reflect.io.RootPath

object PickleJar {
  def write(pickleOut: Path, knownProducts: Set[String]): Path = {
    val pj = RootPath(pickleOut, writable = false) // so it doesn't delete the file
    try Files.walkFileTree(pj.root, deleteUnknowns(knownProducts))
    finally pj.close()
  }

  def deleteUnknowns(knownProducts: Set[String]) = new SimpleFileVisitor[Path] {
    override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
      val ps = path.toString
      if (ps.endsWith(".sig")) {
        // "/foo/bar/wiz.sig" -> "foo/bar/wiz.class"
        if (!knownProducts.contains(ps.stripPrefix("/").stripSuffix(".sig") + ".class"))
          Files.delete(path)
      }
      FileVisitResult.CONTINUE
    }
  }
}
