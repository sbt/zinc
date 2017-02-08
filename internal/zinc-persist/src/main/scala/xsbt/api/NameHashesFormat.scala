/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

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
