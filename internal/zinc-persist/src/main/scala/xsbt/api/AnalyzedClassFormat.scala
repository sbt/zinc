/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package xsbt.api

import xsbti.api._
import sbinary._
import sbinary.DefaultProtocol._
import sbt.internal.inc.APIs.emptyCompanions

object AnalyzedClassFormats {
  // This will throw out API information intentionally.
  def analyzedClassFormat(implicit ev0: Format[Compilation], ev1: Format[NameHashes]): Format[AnalyzedClass] =
    wrap[AnalyzedClass, (Compilation, String, Int, NameHashes, Boolean)](
      a => (a.compilation, a.name, a.apiHash, a.nameHashes, a.hasMacro),
      (x: (Compilation, String, Int, NameHashes, Boolean)) => x match {
        case (compilation: Compilation, name: String, apiHash: Int, nameHashes: NameHashes, hasMacro: Boolean) =>
          new AnalyzedClass(compilation, name, SafeLazyProxy(emptyCompanions), apiHash, nameHashes, hasMacro)
      }
    )
}
final class InputWrapperStream(in: Input) extends java.io.InputStream {
  def toInt(b: Byte) = if (b < 0) b + 256 else b.toInt
  def read() = try { toInt(in.readByte) } catch { case e: sbinary.EOF => -1 }
  override def read(b: Array[Byte], off: Int, len: Int) = in.readTo(b, off, len)
}
final class OutputWrapperStream(out: Output) extends java.io.OutputStream {
  override def write(bs: Array[Byte], off: Int, len: Int) = out.writeAll(bs, off, len)
  def write(b: Int) = out.writeByte(b.toByte)
}
