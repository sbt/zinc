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

import net.openhft.hashing.LongHashFunction

import java.io.{ BufferedInputStream, InputStream }
import java.nio.file.{ Files, Path }
import sbt.io.Hash

object HashUtil {
  def toFarmHashString(digest: Long): String =
    s"farm64-${digest.toHexString}"

  def farmHash(bytes: Array[Byte]): Long =
    LongHashFunction.farmNa().hashBytes(bytes)

  def farmHash(path: Path): Long = {
    import sbt.io.Hash

    //allocating many byte arrays for large files may lead to OOME
    //but it is more efficient for small files
    val largeFileLimit = 10 * 1024 * 1024

    if (Files.size(path) < largeFileLimit)
      farmHash(Files.readAllBytes(path))
    else
      farmHash(Hash(path.toFile))
  }

  def sha256Hash(input: InputStream): Array[Byte] = {
    val BufferSize = 8192
    import java.security.{ DigestInputStream, MessageDigest }
    val bis = new BufferedInputStream(input)
    val digest = MessageDigest.getInstance("SHA-256")
    try {
      val dis = new DigestInputStream(bis, digest)
      val buffer = new Array[Byte](BufferSize)
      while (dis.read(buffer) >= 0) {}
      dis.close()
      digest.digest
    } finally {
      bis.close()
    }
  }

  def sha256HashStr(input: InputStream): String =
    "sha256-" + Hash.toHex(sha256Hash(input))
}
