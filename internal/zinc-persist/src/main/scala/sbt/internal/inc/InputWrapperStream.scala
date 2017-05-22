package sbt.internal.inc

import sbinary.Input

final class InputWrapperStream(in: Input) extends java.io.InputStream {
  def toInt(b: Byte) = if (b < 0) b + 256 else b.toInt
  def read() = try { toInt(in.readByte) } catch { case e: sbinary.EOF => -1 }
  override def read(b: Array[Byte], off: Int, len: Int) = in.readTo(b, off, len)
}
