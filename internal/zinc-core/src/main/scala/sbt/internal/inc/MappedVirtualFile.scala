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

class MappedVirtualFile(encodedPath: String, rootPathsMap: Map[String, Path])
    extends BasicVirtualFileRef(encodedPath)
    with PathBasedFile {
  private def path: Path = {
    rootPathsMap.toSeq.find({
      case (idx, p) =>
        encodedPath.startsWith("${" + idx + "}/")
    }) match {
      case Some((idx, p)) =>
        p.resolve(encodedPath.drop(("${" + idx + "}/").size))
      case None =>
        Paths.get(encodedPath)
    }
  }
  override def contentHash: Long = HashUtil.farmHash(path)
  override def input(): InputStream = Files.newInputStream(path)
  def toPath(): Path = path
}

object MappedVirtualFile {
  def apply(encodedPath: String, rootPaths: Map[String, Path]): MappedVirtualFile =
    new MappedVirtualFile(encodedPath, rootPaths)
}

class MappedFileConverter(rootPaths: Map[String, Path], allowMachinePath: Boolean)
    extends FileConverter {
  val rootPathIdxUri: Seq[(String, String)] = rootPaths.toSeq flatMap {
    case (idx, p) =>
      val u = p.toUri.toString
      if (u.startsWith("file:///var/folders/"))
        Seq(
          idx -> u,
          idx -> u.replaceAllLiterally("file:///var/folders/", "file:///private/var/folders/")
        )
      else Seq(idx -> u)
  }

  def toPath(ref: VirtualFileRef): Path = MappedVirtualFile(ref.id, rootPaths).toPath
  def toVirtualFile(path: Path): VirtualFile = {
    val s = path.toUri.toString
    rootPathIdxUri
      .find({ case (idx, p) => s.startsWith(p) }) match {
      case Some((idx, p)) =>
        val encodedPath = s
          .replaceAllLiterally(p, "${" + idx + "}/")
          .replaceAllLiterally("%20", " ")
        MappedVirtualFile(encodedPath, rootPaths)
      case _ =>
        val fileName = path.getFileName.toString
        fileName match {
          case "rt.jar" => DummyVirtualFile("rt.jar", path)
          case _ =>
            if (allowMachinePath) MappedVirtualFile(path.toString, rootPaths)
            else sys.error(s"$s cannot be mapped using the root paths $rootPaths")
        }
    }
  }
  def toVirtualFile(ref: VirtualFileRef): VirtualFile = MappedVirtualFile(ref.id, rootPaths)
}

object MappedFileConverter {
  def empty: MappedFileConverter = new MappedFileConverter(Map(), true)
  def apply(rootPaths: Map[String, Path], allowMachinePath: Boolean): MappedFileConverter =
    new MappedFileConverter(rootPaths, allowMachinePath)
}
