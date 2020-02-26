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
import xsbti.{ BasicVirtualFileRef, FileConverter, PathBasedFile, VirtualFile, VirtualFileRef }

class MappedVirtualFile(encodedPath: String, rootPathsMap: Map[Int, Path])
    extends BasicVirtualFileRef(encodedPath)
    with PathBasedFile {
  private def path: Path = {
    rootPathsMap.toSeq.find({
      case (idx, p) =>
        encodedPath.startsWith("${" + idx.toString + "}/")
    }) match {
      case Some((idx, p)) =>
        p.resolve(encodedPath.drop(("${" + idx.toString + "}/").size))
      case None =>
        Paths.get(encodedPath)
    }
  }
  override def contentHash: Long = HashUtil.farmHash(path)
  override def input(): InputStream = Files.newInputStream(path)
  def toPath(): Path = path
}

object MappedVirtualFile {
  def apply(encodedPath: String, rootPathsMap: Map[Int, Path]): MappedVirtualFile =
    new MappedVirtualFile(encodedPath, rootPathsMap)
}

class MappedFileConverter(rootPaths: Seq[Path], allowMachinePath: Boolean) extends FileConverter {
  val rootPathIdx = rootPaths.zipWithIndex.map({ case (p, idx) => idx -> p })
  val rootPathIdxUri = rootPathIdx flatMap {
    case (idx, p) =>
      val u = p.toUri.toString
      if (u.startsWith("file:///var/folders/"))
        Seq(
          idx -> u,
          idx -> u.replaceAllLiterally("file:///var/folders/", "file:///private/var/folders/")
        )
      else Seq(idx -> u)
  }
  val rootPathsMap: Map[Int, Path] = Map(rootPathIdx: _*)

  def toPath(ref: VirtualFileRef): Path = MappedVirtualFile(ref.id, rootPathsMap).toPath
  def toVirtualFile(path: Path): VirtualFile = {
    val s = path.toUri.toString
    rootPathIdxUri
      .find({ case (idx, p) => s.startsWith(p) }) match {
      case Some((idx, p)) =>
        val encodedPath = s
          .replaceAllLiterally(p, "${" + idx.toString + "}/")
          .replaceAllLiterally("%20", " ")
        MappedVirtualFile(encodedPath, rootPathsMap)
      case _ =>
        val fileName = path.getFileName.toString
        fileName match {
          case "rt.jar" => DummyVirtualFile("rt.jar", path)
          case _ =>
            if (allowMachinePath) MappedVirtualFile(path.toString, rootPathsMap)
            else sys.error(s"$s cannot be mapped using the root paths $rootPathIdx")
        }
    }
  }
  def toVirtualFile(ref: VirtualFileRef): VirtualFile = MappedVirtualFile(ref.id, rootPathsMap)
}

object MappedFileConverter {
  def empty: MappedFileConverter = new MappedFileConverter(Vector.empty, true)
  def apply(rootPaths: Seq[Path], allowMachinePath: Boolean): MappedFileConverter =
    new MappedFileConverter(rootPaths, allowMachinePath)
}
