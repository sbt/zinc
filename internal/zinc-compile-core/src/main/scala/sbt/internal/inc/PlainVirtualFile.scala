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
import xsbti.{ BasicVirtualFileRef, FileConverter, PathBasedFile, VirtualFileRef, VirtualFile }

class PlainVirtualFile(path: Path) extends BasicVirtualFileRef(path.toString) with PathBasedFile {
  override def contentHash: Long = HashUtil.farmHash(path)
  override def contentHashStr: String = HashUtil.toFarmHashString(contentHash)
  override def name(): String = path.getFileName.toString
  override def input(): InputStream = Files.newInputStream(path)
  override def toPath: Path = path
}
object PlainVirtualFile {
  def apply(path: Path): PlainVirtualFile = new PlainVirtualFile(path)

  // This doesn't use FileConverter
  def extractPath(vf: VirtualFile): Path =
    vf match {
      case x: PathBasedFile => x.toPath
      case _                => sys.error(s"unsupported file: $vf (${vf.getClass})")
    }
}

class PlainVirtualFileConverter extends FileConverter {
  def toPath(ref: VirtualFileRef): Path = ref match {
    case x: PathBasedFile => x.toPath
    case _                => Paths.get(ref.id)
  }

  def toVirtualFile(path: Path): VirtualFile = PlainVirtualFile(path)
}

object PlainVirtualFileConverter {
  val converter = new PlainVirtualFileConverter
}
