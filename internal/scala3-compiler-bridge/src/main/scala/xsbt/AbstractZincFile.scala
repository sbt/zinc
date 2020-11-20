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

package xsbt

import xsbti.{ PathBasedFile, VirtualFile }
import dotty.tools.io

private trait AbstractZincFile extends io.AbstractFile {
  def underlying: VirtualFile
}

private final class ZincPlainFile(val underlying: PathBasedFile)
    extends io.PlainFile(io.Path(underlying.toPath))
    with AbstractZincFile

private final class ZincVirtualFile(val underlying: VirtualFile)
    extends io.VirtualFile(underlying.name, underlying.id)
    with AbstractZincFile {
  io.Streamable.closing(output)(_.write(io.Streamable.bytes(underlying.input))) // fill in the content
}

private object AbstractZincFile {
  def apply(virtualFile: VirtualFile): AbstractZincFile = virtualFile match {
    case file: PathBasedFile => new ZincPlainFile(file)
    case _                   => new ZincVirtualFile(virtualFile)
  }

  def unapply(file: io.AbstractFile): Option[VirtualFile] = file match {
    case wrapper: AbstractZincFile => Some(wrapper.underlying)
    case _                         => None
  }
}
