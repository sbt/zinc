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

import java.nio.file.{ Path, Paths }
import xsbti.{ FileConverter, PathBasedFile, VirtualFile, VirtualFileRef }

class MappedFileConverter(rootPaths: Map[String, Path], allowMachinePath: Boolean)
    extends FileConverter {
  private val rootPaths2: Seq[(String, Path)] = rootPaths.toSeq.flatMap {
    case (key, rootPath) =>
      if (rootPath.startsWith("/var/") || rootPath.startsWith("/tmp/")) {
        val rootPath2 = Paths.get("/private").resolve(Paths.get("/").relativize(rootPath))
        Seq(key -> rootPath, key -> rootPath2)
      } else Seq(key -> rootPath)
  }

  override def toPath(ref: VirtualFileRef): Path = ref match {
    case x: PathBasedFile => x.toPath
    case _ =>
      rootPaths.toSeq.find { case (key, _) => ref.id.startsWith(s"$${$key}/") } match {
        case Some((key, p)) => p.resolve(ref.id.stripPrefix(s"$${$key}/"))
        case None           => Paths.get(ref.id)
      }
  }

  override def toVirtualFile(path: Path): VirtualFile = {
    rootPaths2.find { case (_, rootPath) => path.startsWith(rootPath) } match {
      case Some((key, rootPath)) =>
        PlainVirtualFile(s"$${$key}/${rootPath.relativize(path)}".replace('\\', '/'), path)
      case _ =>
        path.getFileName.toString match {
          case "rt.jar"              => PlainVirtualFile("rt.jar", path)
          case _ if allowMachinePath => PlainVirtualFile(s"$path".replace('\\', '/'), path)
          case _                     => sys.error(s"$path cannot be mapped using the root paths $rootPaths")
        }
    }
  }
}

object MappedFileConverter {
  def apply(rootPaths: Map[String, Path], allowMachinePath: Boolean): MappedFileConverter =
    new MappedFileConverter(rootPaths, allowMachinePath)
}
