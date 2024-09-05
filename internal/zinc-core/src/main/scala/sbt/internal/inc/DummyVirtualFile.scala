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

import java.io.InputStream
import java.nio.file.{ Files, Path }
import xsbti.{ BasicVirtualFileRef, VirtualFile }

/**
 * This is a dummy VirtualFile that's a simple wrapper around Path.
 */
class DummyVirtualFile(encodedPath: String, path: Path)
    extends BasicVirtualFileRef(encodedPath)
    with VirtualFile {
  override def contentHash: Long = HashUtil.farmHash(path)
  override def sizeBytes: Long = Files.size(path)
  override lazy val contentHashStr: String = HashUtil.sha256HashStr(input)
  override def input(): InputStream = Files.newInputStream(path)
}

object DummyVirtualFile {
  def apply(encodedPath: String, path: Path): DummyVirtualFile =
    new DummyVirtualFile(encodedPath, path)
}
