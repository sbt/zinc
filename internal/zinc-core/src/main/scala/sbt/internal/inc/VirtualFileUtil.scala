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

import java.io.File
import java.nio.file.Path
import java.util.Optional

import xsbti.{ FileConverter, PathBasedFile, Position, VirtualFile, VirtualFileRef }
import xsbti.compile.Output
import sbt.util.InterfaceUtil._

object VirtualFileUtil {
  implicit val sbtInternalIncVirtualFileOrdering: Ordering[VirtualFile] = Ordering.by(_.id)
  implicit val sbtInternalIncVirtualFileRefOrdering: Ordering[VirtualFileRef] = Ordering.by(_.id)

  def outputDirectory(output: Output): Path =
    output.getSingleOutputAsPath.orElseThrow(() =>
      new RuntimeException(s"unexpected output $output")
    )

  def sourcePositionMapper(converter: FileConverter): Position => Position =
    new DelegatingPosition(_, converter)

  class DelegatingPosition(original: Position, converter: FileConverter) extends Position {
    override def line = original.line
    override def lineContent = original.lineContent
    override def offset = original.offset

    private var sourcePath0 = original.sourcePath
    private val sourceFile0 = sourcePath0.map[File] { p =>
      if (p.contains("${")) {
        val path = converter.toPath(VirtualFileRef.of(p))
        sourcePath0 = Optional.of(path.toString)
        path.toFile
      } else {
        new File(p)
      }
    }

    override val sourcePath = sourcePath0
    override val sourceFile = sourceFile0
    override def pointer = original.pointer
    override def pointerSpace = original.pointerSpace
    override def startOffset = original.startOffset
    override def endOffset = original.endOffset
    override def startLine = original.startLine
    override def startColumn = original.startColumn
    override def endLine = original.endLine
    override def endColumn = original.endColumn

    override def toString = (jo2o(sourcePath0), jo2o(line)) match {
      case (Some(s), Some(l)) => s"$s:$l"
      case (Some(s), _)       => s"$s:"
      case _                  => ""
    }
  }

  def toAbsolute(vf: VirtualFile): VirtualFile = vf match {
    case x: PathBasedFile if !x.toPath.isAbsolute => PlainVirtualFile(x.toPath.toAbsolutePath)
    case _                                        => vf
  }
}
