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

class BasicVirtualDirectory(val name: String, parent: Option[VirtualDirectory])
    extends VirtualDirectory {
  override def fileNamed(name: String): xsbti.VirtualFileWrite = {
    val newFile = new BasicMemoryFile(name, Some(this))
    newFile
  }
  override def subdirectoryNamed(name: String): BasicVirtualDirectory = {
    val newDir = new BasicVirtualDirectory(name, Some(this))
    newDir
  }
  override def id: String = BasicVirtualDirectory.parentId(parent) + name + "/"
  override def names: Array[String] =
    BasicVirtualDirectory.parentNames(parent) ++
      (if (name == "") Array.empty[String]
       else Array(name))
  override def toString: String = id
}

object BasicVirtualDirectory {
  def newRoot: BasicVirtualDirectory = apply("")
  def apply(name: String): BasicVirtualDirectory = new BasicVirtualDirectory(name, None)

  private[inc] def parentId(parent: Option[VirtualDirectory]): String =
    parent match {
      case Some(p) => p.id
      case None    => ""
    }
  private[inc] def parentNames(parent: Option[VirtualDirectory]): Array[String] =
    parent match {
      case Some(p) => p.names
      case None    => Array.empty[String]
    }
}

class BasicMemoryFile(val name: String, parent: Option[VirtualDirectory]) extends VirtualFileWrite {
  private[this] val byteArray = new ByteArrayOutputStream()
  override def contentHash(): Long = HashUtil.farmHash(byteArray.toByteArray())
  override def input(): InputStream = new ByteArrayInputStream(byteArray.toByteArray())
  override def output(): OutputStream = byteArray
  override def id(): String = BasicVirtualDirectory.parentId(parent) + name
  override def names(): Array[String] = BasicVirtualDirectory.parentNames(parent) ++ Array(name)
  override def toString: String = id
}
