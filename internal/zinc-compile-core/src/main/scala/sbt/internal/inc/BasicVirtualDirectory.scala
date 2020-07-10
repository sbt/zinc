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

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream }
import xsbti.{ VirtualDirectory, VirtualFileWrite };

class BasicVirtualDirectory private (parts: List[String]) extends VirtualDirectory {
  override def fileNamed(name: String): VirtualFileWrite = new BasicMemoryFile(name, this)

  override def subdirectoryNamed(name: String): BasicVirtualDirectory =
    new BasicVirtualDirectory(name :: parts)

  override def id: String = ("" :: parts).reverseIterator.mkString("/", "/", "")
  override def names(): Array[String] = parts.reverseIterator.toArray
  override def name(): String = if (parts.isEmpty) "" else parts.head
  override def toString: String = id
}

object BasicVirtualDirectory {
  def newRoot: BasicVirtualDirectory = new BasicVirtualDirectory(Nil)
  def apply(name: String): BasicVirtualDirectory =
    if (name == "") newRoot else new BasicVirtualDirectory(List(name))
}

class BasicMemoryFile(val name: String, parent: VirtualDirectory) extends VirtualFileWrite {
  private[this] val byteArray = new ByteArrayOutputStream()
  override def contentHash: Long = HashUtil.farmHash(byteArray.toByteArray())
  override def input: InputStream = new ByteArrayInputStream(byteArray.toByteArray())
  override def output: OutputStream = byteArray
  override def id: String = s"$parent$name"
  override def names: Array[String] = parent.names :+ name
  override def toString: String = id
}
