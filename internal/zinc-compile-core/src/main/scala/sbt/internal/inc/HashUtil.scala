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

import java.nio.file.{ Files, Path }
import net.openhft.hashing.LongHashFunction

object HashUtil {
  def farmHash(bytes: Array[Byte]): Long = LongHashFunction.farmNa().hashBytes(bytes)

  def farmHash(path: Path): Long = farmHash(Files.readAllBytes(path))
}
