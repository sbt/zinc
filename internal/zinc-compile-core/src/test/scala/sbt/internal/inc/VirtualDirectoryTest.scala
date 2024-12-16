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

package sbt
package internal
package inc

import java.io.{ InputStreamReader, OutputStreamWriter }
import verify._

object VirtualDirectoryTest extends BasicTestSuite {
  test("root name is /") {
    val root = BasicVirtualDirectory.newRoot
    assert(root.id == "/")
  }

  test("subdirectory") {
    val root = BasicVirtualDirectory.newRoot
    val sub1 = root.subdirectoryNamed("sub1")
    assert(sub1.id == "/sub1/")
    assert(sub1.names.toList == List("sub1"))
  }

  test("in-memory file") {
    val root = BasicVirtualDirectory.newRoot
    val item = root.fileNamed("item")
    assert(item.id == "/item")
    assert(item.names.toList == List("item"))

    val out = item.output
    val w = new OutputStreamWriter(out, "UTF-8")
    w.write("hello")
    w.flush()
    val r = new InputStreamReader(item.input, "UTF-8")
    val buf = new Array[Char](1024)
    r.read(buf, 0, 1024)
    assertEquals("hello", new String(buf).trim)

    assert(item.contentHash != 0L)
  }
}
