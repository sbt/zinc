package xsbt.api

import xsbti.api._
import sbinary._

object CompanionsFormat extends Format[Companions] {
  import java.io._
  def reads(in: Input): Companions = {
    val oin = new ObjectInputStream(new InputWrapperStream(in))
    try { oin.readObject.asInstanceOf[Companions] } finally { oin.close() }
  }
  def writes(out: Output, src: Companions): Unit = {
    val oout = new ObjectOutputStream(new OutputWrapperStream(out))
    try { oout.writeObject(src) } finally { oout.close() }
  }
}
