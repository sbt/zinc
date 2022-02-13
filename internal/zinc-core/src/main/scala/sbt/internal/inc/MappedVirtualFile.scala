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
  private def path: Path = MappedVirtualFile.toPath(encodedPath, rootPathsMap)
  override def contentHash: Long = HashUtil.farmHash(path)
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

class MappedFileConverter(rootPaths: Map[String, Path], allowMachinePath: Boolean)
    extends FileConverter {
  val rootPaths2: Seq[(String, Path)] = rootPaths.toSeq.flatMap { case (key, rootPath) =>
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
    rootPaths2.find { case (_, rootPath) => path.startsWith(rootPath) } match {
      case Some((key, rootPath)) =>
        MappedVirtualFile(s"$${$key}/${rootPath.relativize(path)}".replace('\\', '/'), rootPaths)
      case _ =>
        def isCtSym =
          path.getFileSystem
            .provider()
            .getScheme == "jar" && path.getFileSystem.toString.endsWith("ct.sym")
        def isJrt = path.getFileSystem.provider().getScheme == "jrt"
        if (isJrt || path.getFileName.toString == "rt.jar" || isCtSym)
          DummyVirtualFile("rt.jar", path)
        else if (allowMachinePath) MappedVirtualFile(s"$path".replace('\\', '/'), rootPaths)
        else sys.error(s"$path cannot be mapped using the root paths $rootPaths")
    }
  }
}

object MappedFileConverter {
  def empty: MappedFileConverter = new MappedFileConverter(Map(), true)
  def apply(rootPaths: Map[String, Path], allowMachinePath: Boolean): MappedFileConverter =
    new MappedFileConverter(rootPaths, allowMachinePath)
}
