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

package sbt.internal.inc.text

private[sbt] trait Base64 {
  def encode(bytes: Array[Byte]): String

  def decode(string: String): Array[Byte]
}

private[sbt] object Base64 {
  lazy val factory: () => Base64 = { () => new Java89Encoder }
}

private[sbt] object Java89Encoder {}

private[sbt] class Java89Encoder extends Base64 {
  def encode(bytes: Array[Byte]): String = java.util.Base64.getEncoder.encodeToString(bytes)

  def decode(string: String): Array[Byte] = java.util.Base64.getDecoder.decode(string)
}
