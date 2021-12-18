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

import java.nio.channels.FileChannel
import java.nio.file.{ Path, StandardOpenOption }
import java.nio.ByteBuffer
import net.openhft.hashing.LongHashFunction

object HashUtil {
  def farmHash(bytes: Array[Byte]): Long =
    LongHashFunction.farmNa().hashBytes(bytes)

  def farmHash(buffer: ByteBuffer): Long =
    LongHashFunction.farmNa().hashBytes(buffer)

  def farmHash(path: Path): Long =
    farmHash(mappedBuffer(path))

  private def mappedBuffer(path: Path): ByteBuffer = {
    val fileChannel = FileChannel.open(path, StandardOpenOption.READ)
    fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
  }
}
