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

import sbinary._
import xsbti.api._

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
