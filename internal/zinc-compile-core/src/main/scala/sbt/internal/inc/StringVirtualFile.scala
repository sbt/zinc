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

import xsbti.{ BasicVirtualFileRef, VirtualFile }

case class StringVirtualFile(path: String, content: String)
    extends BasicVirtualFileRef(path)
    with VirtualFile {
  override def contentHash: Long = HashUtil.farmHash(content.getBytes("UTF-8"))
  override def input: InputStream = new ByteArrayInputStream(content.getBytes("UTF-8"))
  override def toString: String = s"StringVirtualFile($path, <content>)"
}
