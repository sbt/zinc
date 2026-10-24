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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.io.{ InputStreamReader, OutputStreamWriter }

object VirtualDirectoryTest extends Properties:
  override def tests: List[Test] = List(
    example("root name is /", rootName),
    example("subdirectory", subdirectory),
    example("in-memory file", inMemoryFile),
  )

  private def rootName: Result =
    val root = BasicVirtualDirectory.newRoot
    root.id ==== "/"

  private def subdirectory: Result =
    val root = BasicVirtualDirectory.newRoot
    val sub1 = root.subdirectoryNamed("sub1")
    Result.all(List(sub1.id ==== "/sub1/", sub1.names.toList ==== List("sub1")))

  private def inMemoryFile: Result =
    val root = BasicVirtualDirectory.newRoot
    val item = root.fileNamed("item")
    val names = Result.all(List(item.id ==== "/item", item.names.toList ==== List("item")))

    val out = item.output
    val w = new OutputStreamWriter(out, "UTF-8")
    w.write("hello")
    w.flush()
    val r = new InputStreamReader(item.input, "UTF-8")
    val buf = new Array[Char](1024)
    r.read(buf, 0, 1024)
    Result.all(
      List(
        names,
        new String(buf).trim ==== "hello",
        Result.diff(item.contentHash, 0L)(_ != _),
      )
    )
end VirtualDirectoryTest
