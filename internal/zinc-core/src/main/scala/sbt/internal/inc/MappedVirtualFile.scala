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

import java.io.{ ByteArrayInputStream, IOException, InputStream }
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.nio.file.{ Files, Path, Paths }
import java.nio.file.attribute.{ BasicFileAttributes, FileTime }
import java.util.concurrent.ConcurrentHashMap
import xsbti.{ BasicVirtualFileRef, FileConverter, PathBasedFile, VirtualFile, VirtualFileRef }
import sbt.nio.file.{ FileTreeView, Glob, IsNotHidden, IsRegularFile, RecursiveGlob }

class MappedVirtualFile(encodedPath: String, rootPathsMap: Map[String, Path])
    extends BasicVirtualFileRef(encodedPath)
    with PathBasedFile {
  private def path: Path = MappedVirtualFile.toPath(encodedPath, rootPathsMap)
  override lazy val contentHash: Long = HashUtil.farmHash(path)
  override def sizeBytes: Long = Files.size(path)
  override lazy val contentHashStr: String = HashUtil.sha256HashStr(input)
  override def input(): InputStream = Files.newInputStream(path)
  override def toPath: Path = path
}

object MappedVirtualFile {
  def apply(encodedPath: String, rootPaths: Map[String, Path]): MappedVirtualFile =
    new MappedVirtualFile(encodedPath, rootPaths)

  private[inc] def toPathMapped[A](encodedPath: String, rootPaths: Map[String, Path])(
      modifyResolvedPath: Path => A,
      modifyOrigPath: String => A
  ): A =
    rootPaths.toSeq.find { case (key, _) => encodedPath.startsWith(s"$${$key}/") } match {
      case Some((key, p)) => modifyResolvedPath(p.resolve(encodedPath.stripPrefix(s"$${$key}/")))
      case None           => modifyOrigPath(encodedPath)
    }

  def toPath(encodedPath: String, rootPaths: Map[String, Path]): Path =
    toPathMapped(encodedPath, rootPaths)(identity, Paths.get(_))
}

class MappedDirectory(
    encodedPath: String,
    rootPathsMap: Map[String, Path],
    items0: => List[VirtualFile]
) extends BasicVirtualFileRef(encodedPath)
    with PathBasedFile {
  private def path: Path = MappedVirtualFile.toPath(encodedPath, rootPathsMap)

  // Only the hashes below read `items`; most consumers of a directory entry want just `toPath` or
  // `definesClass`, so listing eagerly statted every classpath directory for nothing. The hashes
  // therefore describe the directory as of first access.
  lazy val items: List[VirtualFile] = items0

  override lazy val contentHash: Long = {
    val buffer = ByteBuffer.allocate(java.lang.Long.BYTES * items.size)
    items.foreach { item =>
      buffer.putLong(item.contentHash)
    }
    HashUtil.farmHash(buffer.array())
  }
  override lazy val sizeBytes: Long = items.map(_.sizeBytes).sum
  override lazy val contentHashStr: String = {
    val sb = new StringBuilder
    items.foreach { item =>
      sb.append(item.contentHashStr)
    }
    val stream = new ByteArrayInputStream(sb.toString.getBytes("UTF-8"))
    HashUtil.sha256HashStr(stream)
  }
  override def input(): InputStream = ???
  override def toPath: Path = path
}

object MappedDirectory {
  def apply(
      encodedPath: String,
      rootPaths: Map[String, Path],
      items: => List[VirtualFile]
  ): MappedDirectory =
    new MappedDirectory(encodedPath, rootPaths, items)
}

class MappedFileConverter(val rootPaths: Map[String, Path], allowMachinePath: Boolean)
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

  // Optimized version that skips isDirectory check - use when path is known to be a regular file
  private def toVirtualFileForRegularFile(path: Path): VirtualFile = {
    rootPaths2.find { case (_, rootPath) => path.startsWith(rootPath) } match {
      case Some((key, rootPath)) =>
        val encodedPath = s"$${$key}/${rootPath.relativize(path)}".replace('\\', '/')
        MappedVirtualFile(encodedPath, rootPaths)
      case _ =>
        if (allowMachinePath) {
          val encodedPath = s"$path".replace('\\', '/')
          MappedVirtualFile(encodedPath, rootPaths)
        } else sys.error(s"$path cannot be mapped using the root paths $rootPaths")
    }
  }

  // Consumers of a shared classpath each convert its entries independently, and a directory entry
  // wraps every contained file, so without interning a shared classes directory is re-materialized
  // once per consumer - value-identical MappedVirtualFiles then accumulate on the heap on large
  // multi-project builds (#1750). Keying by (lastModified, size) keeps a memoized content hash from
  // being served across a content change, and the SoftReference bounds retention of this long-lived
  // shared converter. Top-level conversions are deliberately excluded so they stay per-consumer:
  // their lazy hashes must read content at first access, which incremental source change detection
  // relies on.
  private val itemCache =
    new ConcurrentHashMap[Path, ((FileTime, Long), SoftReference[VirtualFile])]

  private def toItem(path: Path): VirtualFile =
    try {
      val attrs = Files.readAttributes(path, classOf[BasicFileAttributes])
      val metadata = (attrs.lastModifiedTime(), attrs.size())
      val cached = itemCache.get(path)
      val hit = if (cached != null && cached._1 == metadata) cached._2.get() else null
      if (hit != null) hit
      else {
        val vf = toVirtualFileForRegularFile(path)
        itemCache.put(path, (metadata, new SoftReference(vf)))
        vf
      }
    } catch { case _: IOException => toVirtualFileForRegularFile(path) }

  def toDirectory(path: Path, encodedPath: String): MappedDirectory = {
    // Passed by name, so the walk runs on first access to items rather than here
    def items = view
      .list(Glob(path, RecursiveGlob), IsRegularFile && IsNotHidden)
      .map(_._1)
      .sorted
      .map(toItem)
      .toList
    MappedDirectory(encodedPath, rootPaths, items)
  }
}

object MappedFileConverter {
  private[sbt] lazy val view = FileTreeView.Ops(FileTreeView.default)

  def empty: MappedFileConverter = new MappedFileConverter(Map(), true)
  def apply(rootPaths: Map[String, Path], allowMachinePath: Boolean): MappedFileConverter =
    new MappedFileConverter(rootPaths, allowMachinePath)
}
