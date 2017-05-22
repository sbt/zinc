package sbt.internal.inc

import sbinary.Output

final class OutputWrapperStream(out: Output) extends java.io.OutputStream {
  override def write(bs: Array[Byte], off: Int, len: Int) = out.writeAll(bs, off, len)
  def write(b: Int) = out.writeByte(b.toByte)
}
