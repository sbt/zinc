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
package javac

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

import sbt.io.PathFinder

import scala.util.control.NonFatal

trait Classes {
  def paths: Seq[Path]
  final def pathsAndClose(): Seq[Path] = {
    try paths
    finally close()
  }
  def close(): Unit = ()
}
object Classes {
  object empty extends Classes {
    override def paths: Seq[Path] = Nil
  }
}

trait ClassFinder {
  def classes: Classes
}

class DirectoryClassFinder(dir: Path) extends ClassFinder {
  class DirectoryClasses(val paths: Seq[Path]) extends Classes

  private val pathFinder = PathFinder(dir.toFile) ** "*.class"

  override def classes: DirectoryClasses = new DirectoryClasses(pathFinder.get().map(_.toPath))
}

class JarClassFinder(jar: Path) extends ClassFinder {
  class JarClasses(val paths: Seq[Path], fs: FileSystem) extends Classes {
    override def close(): Unit = {
      fs.close()
      super.close()
    }
  }

  override def classes: Classes =
    if (Files.exists(jar)) {
      val jarFs = FileSystems.newFileSystem(jar, null.asInstanceOf[ClassLoader])
      try {
        val stream = Files.find(
          jarFs.getRootDirectories.iterator().next(),
          Int.MaxValue,
          (path, _) => path.toString.endsWith(".class")
        )
        try {
          val builder = Seq.newBuilder[Path]
          stream.forEachOrdered((a: Path) => builder += a)
          new JarClasses(builder.result(), jarFs)
        } finally stream.close()
      } catch {
        case NonFatal(t) =>
          jarFs.close()
          throw t
      }
    } else Classes.empty
}
