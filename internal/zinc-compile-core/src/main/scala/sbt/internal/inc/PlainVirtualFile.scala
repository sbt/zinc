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

import java.io.InputStream
import java.nio.file.{ Files, Path, Paths }
import xsbti.{ BasicVirtualFileRef, FileConverter, PathBasedFile, VirtualFileRef }

/** A virtual file reference, with an associated id (possibly encoded) and its true path. */
final class PlainVirtualFile(id: String, path: Path)
    extends BasicVirtualFileRef(id)
    with PathBasedFile {
  override def contentHash: Long = HashUtil.farmHash(path)
  override def input: InputStream = Files.newInputStream(path)
  override def toPath: Path = path
}

object PlainVirtualFile {
  def apply(id: String, path: Path): PlainVirtualFile = new PlainVirtualFile(id, path)
  def apply(path: Path): PlainVirtualFile = apply(path.toString, path)
}

class PlainVirtualFileConverter extends FileConverter {
  def toPath(ref: VirtualFileRef): Path = ref match {
    case x: PathBasedFile => x.toPath
    case _                => Paths.get(ref.id)
  }

  override def toVirtualFile(path: Path): PlainVirtualFile = PlainVirtualFile(path)
}

object PlainVirtualFileConverter {
  val converter = new PlainVirtualFileConverter
}
