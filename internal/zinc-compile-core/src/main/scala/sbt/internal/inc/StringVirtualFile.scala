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

import java.io.{ ByteArrayInputStream, InputStream }

import xsbti.{ BasicVirtualFileRef, VirtualFile }

case class StringVirtualFile(path: String, content: String)
    extends BasicVirtualFileRef(path)
    with VirtualFile {
  override def contentHash: Long = HashUtil.farmHash(content.getBytes("UTF-8"))
  override def contentHashStr: String = HashUtil.toFarmHashString(contentHash)
  override def input: InputStream = new ByteArrayInputStream(content.getBytes("UTF-8"))
  override def toString: String = s"StringVirtualFile($path, <content>)"
}
