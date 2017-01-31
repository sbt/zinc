/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt.api

import xsbti.api._
import sbinary._

object NameHashFormat extends Format[NameHash] {
  import java.io._
  def reads(in: Input): NameHash =
    {
      val oin = new ObjectInputStream(new InputWrapperStream(in))
      try { oin.readObject.asInstanceOf[NameHash] } finally { oin.close() }
    }
  def writes(out: Output, src: NameHash): Unit = {
    val oout = new ObjectOutputStream(new OutputWrapperStream(out))
    try { oout.writeObject(src) } finally { oout.close() }
  }
}
