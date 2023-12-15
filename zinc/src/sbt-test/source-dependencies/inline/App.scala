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
object App {
  def main(args: Array[String]): Unit = {
    val exp = args(0).toInt
    val res = B.x
    if (res != exp) {
      val e = new Exception(s"assertion failed: expected $exp, obtained $res")
      e.setStackTrace(Array())
      throw e
    }
  }
}
