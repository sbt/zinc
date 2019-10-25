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

import java.io.File
import java.nio.file.Path
import xsbti.{ FileConverter, PathBasedFile, Position, VirtualFile, VirtualFileRef }
import xsbti.compile.{ Output, SingleOutput }

object VirtualFileUtil {
  implicit val sbtInternalIncVirtualFileRefOrdering: Ordering[VirtualFileRef] =
    new VirtualFileRefOrdering()
  private class VirtualFileRefOrdering extends Ordering[VirtualFileRef] {
    val strOrdering = implicitly[Ordering[String]]
    override def compare(lhs: VirtualFileRef, rhs: VirtualFileRef): Int = {
      strOrdering.compare(lhs.id, rhs.id)
    }
  }

  implicit val sbtInternalIncVirtualFileOrdering: Ordering[VirtualFile] =
    new VirtualFileOrdering()
  private class VirtualFileOrdering extends Ordering[VirtualFile] {
    val strOrdering = implicitly[Ordering[String]]
    override def compare(lhs: VirtualFile, rhs: VirtualFile): Int = {
      strOrdering.compare(lhs.id, rhs.id)
    }
  }

  def outputDirectory(output: Output): Path =
    output match {
      case single0: SingleOutput => single0.getOutputDirectory
      case _                     => sys.error(s"unexpected output $output")
    }

  def sourcePositionMapper(converter: FileConverter): Position => Position = { pos0: Position =>
    new DelegatingPosition(pos0, converter)
  }

  class DelegatingPosition(
      original: Position,
      converter: FileConverter
  ) extends xsbti.Position {
    override def line = original.line
    override def lineContent = original.lineContent
    override def offset = original.offset

    import sbt.util.InterfaceUtil._
    val popt = toOption(original.sourcePath)
    val (sourcePath0, sourceFile0) = popt match {
      case Some(p) =>
        if (p.contains("${")) {
          val ref = VirtualFileRef.of(p)
          val path = converter.toPath(ref)
          (Some(path.toString), Some(path.toFile))
        } else {
          (Some(p), Some(new File(p)))
        }
      case _ => (None, None)
    }
    override val sourcePath = o2jo(sourcePath0)
    override val sourceFile = o2jo(sourceFile0)
    override def pointer = original.pointer
    override def pointerSpace = original.pointerSpace
    override def startOffset = original.startOffset
    override def endOffset = original.endOffset
    override def startLine = original.startLine
    override def startColumn = original.startColumn
    override def endLine = original.endLine
    override def endColumn = original.endColumn
    override def toString =
      (sourcePath0, jo2o(line)) match {
        case (Some(s), Some(l)) => s + ":" + l
        case (Some(s), _)       => s + ":"
        case _                  => ""
      }
  }

  def toAbsolute(vf: VirtualFile): VirtualFile = {
    vf match {
      case x: PathBasedFile =>
        val p = x.toPath
        if (p.isAbsolute) x
        else PlainVirtualFile(p.toAbsolutePath)
      case _ => vf
    }
  }
}
