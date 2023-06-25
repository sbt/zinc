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

package sbt.internal.inc.text

import sbinary.Input

final class InputWrapperStream(in: Input) extends java.io.InputStream {
  def toInt(b: Byte) = if (b < 0) b + 256 else b.toInt
  def read() =
    try {
      toInt(in.readByte)
    } catch { case _: sbinary.EOF => -1 }
  override def read(b: Array[Byte], off: Int, len: Int) = in.readTo(b, off, len)
}
