package xsbt.api

import xsbti.api._
import sbinary._

object NameHashesFormat extends Format[NameHashes] {
  import java.io._
  def reads(in: Input): NameHashes =
    {
      val oin = new ObjectInputStream(new InputWrapperStream(in))
      try { oin.readObject.asInstanceOf[NameHashes] } finally { oin.close() }
    }
  def writes(out: Output, src: NameHashes): Unit = {
    val oout = new ObjectOutputStream(new OutputWrapperStream(out))
    try { oout.writeObject(src) } finally { oout.close() }
  }
}
