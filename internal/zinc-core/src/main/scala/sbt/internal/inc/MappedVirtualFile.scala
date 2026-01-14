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

import java.io.{ ByteArrayInputStream, InputStream }
import java.nio.ByteBuffer
import java.nio.file.{ Files, Path, Paths }
import xsbti.{ BasicVirtualFileRef, FileConverter, PathBasedFile, VirtualFile, VirtualFileRef }
import sbt.nio.file.{ FileTreeView, Glob, IsNotHidden, IsRegularFile, RecursiveGlob }

class MappedVirtualFile(encodedPath: String, rootPathsMap: Map[String, Path])
    extends BasicVirtualFileRef(encodedPath)
    with PathBasedFile {
  private def path: Path = MappedVirtualFile.toPath(encodedPath, rootPathsMap)
  override def contentHash: Long = HashUtil.farmHash(path)
  override def sizeBytes: Long = Files.size(path)
  override lazy val contentHashStr: String = HashUtil.sha256HashStr(input)
  override def input(): InputStream = Files.newInputStream(path)
  override def toPath: Path = path
}

object MappedVirtualFile {
  def apply(encodedPath: String, rootPaths: Map[String, Path]): MappedVirtualFile =
    new MappedVirtualFile(encodedPath, rootPaths)

  def toPath(encodedPath: String, rootPaths: Map[String, Path]): Path = {
    rootPaths.toSeq.find { case (key, _) => encodedPath.startsWith(s"$${$key}/") } match {
      case Some((key, p)) => p.resolve(encodedPath.stripPrefix(s"$${$key}/"))
      case None           => Paths.get(encodedPath)
    }
  }
}

class MappedDirectory(
    encodedPath: String,
    rootPathsMap: Map[String, Path],
    items: List[VirtualFile]
) extends BasicVirtualFileRef(encodedPath)
    with PathBasedFile {
  private def path: Path = MappedVirtualFile.toPath(encodedPath, rootPathsMap)
  override lazy val contentHash: Long = {
    val buffer = ByteBuffer.allocate(java.lang.Long.BYTES * items.size)
    val hashes = items.foreach { item =>
      buffer.putLong(item.contentHash)
    }
    HashUtil.farmHash(buffer.array())
  }
  override lazy val sizeBytes: Long = items.map(_.sizeBytes).sum
  override lazy val contentHashStr: String = {
    val sb = new StringBuilder
    val hashes = items.foreach { item =>
      sb.append(item.contentHashStr)
    }
    val stream = new ByteArrayInputStream(hashes.toString.getBytes("UTF-8"))
    HashUtil.sha256HashStr(stream)
  }
  override def input(): InputStream = ???
  override def toPath: Path = path
}

object MappedDirectory {
  def apply(
      encodedPath: String,
      rootPaths: Map[String, Path],
      items: List[VirtualFile]
  ): MappedDirectory =
    new MappedDirectory(encodedPath, rootPaths, items)
}

class MappedFileConverter(rootPaths: Map[String, Path], allowMachinePath: Boolean)
    extends FileConverter {

  import MappedFileConverter.view

  val rootPaths2: Seq[(String, Path)] = rootPaths.toSeq.flatMap {
    case (key, rootPath) =>
      if (rootPath.startsWith("/var/") || rootPath.startsWith("/tmp/")) {
        val rootPath2 = Paths.get("/private").resolve(Paths.get("/").relativize(rootPath))
        Seq(key -> rootPath, key -> rootPath2)
      } else Seq(key -> rootPath)
  }

  def toPath(ref: VirtualFileRef): Path = ref match {
    case x: PathBasedFile => x.toPath
    case _                => MappedVirtualFile.toPath(ref.id, rootPaths)
  }

  def toVirtualFile(path: Path): VirtualFile = {
    def isDirectory: Boolean =
      Files.isDirectory(path) || (!Files.exists(path) && path.getFileName.toString().endsWith(
        "classes"
      ))
    rootPaths2.find { case (_, rootPath) => path.startsWith(rootPath) } match {
      case Some((key, rootPath)) =>
        val encodedPath = s"$${$key}/${rootPath.relativize(path)}".replace('\\', '/')
        if (isDirectory) toDirectory(path, encodedPath)
        else MappedVirtualFile(encodedPath, rootPaths)
      case _ =>
        def isCtSym =
          path.getFileSystem
            .provider()
            .getScheme == "jar" && path.getFileSystem.toString.endsWith("ct.sym")
        def isJrt = path.getFileSystem.provider().getScheme == "jrt"
        if (isJrt || path.getFileName.toString == "rt.jar" || isCtSym)
          DummyVirtualFile("rt.jar", path)
        else if (allowMachinePath) {
          val encodedPath = s"$path".replace('\\', '/')
          if (isDirectory) toDirectory(path, encodedPath)
          else MappedVirtualFile(encodedPath, rootPaths)
        } else sys.error(s"$path cannot be mapped using the root paths $rootPaths")
    }
  }

  def toDirectory(path: Path, encodedPath: String) = {
    val list = view.list(Glob(path, RecursiveGlob), IsRegularFile && IsNotHidden)
      .map(_._1)
      .sorted
    val items = list.map(toVirtualFile)
    MappedDirectory(encodedPath, rootPaths, items.toList)
  }
}

object MappedFileConverter {
  private[sbt] lazy val view = FileTreeView.Ops(FileTreeView.default)

  def empty: MappedFileConverter = new MappedFileConverter(Map(), true)
  def apply(rootPaths: Map[String, Path], allowMachinePath: Boolean): MappedFileConverter =
    new MappedFileConverter(rootPaths, allowMachinePath)
}
