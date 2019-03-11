/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc.text

private[sbt] trait Base64 {
  def encode(bytes: Array[Byte]): String

  def decode(string: String): Array[Byte]
}

private[sbt] object Base64 {
  lazy val factory: () => Base64 = { () =>
    new Java89Encoder
  }
}

private[sbt] object Java89Encoder {}

private[sbt] class Java89Encoder extends Base64 {
  def encode(bytes: Array[Byte]): String =
    java.util.Base64.getEncoder.encodeToString(bytes)

  def decode(string: String): Array[Byte] =
    java.util.Base64.getDecoder.decode(string)
}
