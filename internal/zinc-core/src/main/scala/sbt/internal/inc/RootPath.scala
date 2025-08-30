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

package sbt.internal.inc

import java.io.Closeable
import java.nio
import java.nio.file.Files
import java.nio.file.spi.FileSystemProvider

import scala.jdk.CollectionConverters._

/**
 * borrowed from scala-reflect
 * [[https://github.com/scala/scala/blob/v2.13.16/src/reflect/scala/reflect/io/RootPath.scala]]
 */
private[inc] abstract class RootPath extends Closeable {
  def root: nio.file.Path
}

private[inc] object RootPath {
  private lazy val jarFsProvider = FileSystemProvider.installedProviders().asScala.find(
    _.getScheme == "jar"
  ).getOrElse(throw new RuntimeException("No jar filesystem provider"))
  def apply(path: nio.file.Path, writable: Boolean): RootPath = {
    if (path.getFileName.toString.endsWith(".jar")) {
      val env = new java.util.HashMap[String, String]()
      if (!Files.exists(path.getParent))
        Files.createDirectories(path.getParent)
      if (writable) {
        env.put("create", "true")
        if (Files.exists(path))
          Files.delete(path)
      }
      val zipfs = jarFsProvider.newFileSystem(path, env)

      new RootPath {
        def root = zipfs.getRootDirectories.iterator().next()
        def close(): Unit = {
          zipfs.close()
        }
        override def toString: String = path.toString
      }
    } else {
      new RootPath {
        override def root: nio.file.Path = path
        override def close(): Unit = ()
        override def toString: String = path.toString
      }
    }
  }
}
