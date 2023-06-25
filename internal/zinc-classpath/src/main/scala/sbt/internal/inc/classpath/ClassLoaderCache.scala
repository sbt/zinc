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
package classpath

import java.io.File
import java.lang.ref.{ Reference, SoftReference }
import java.net.URLClassLoader
import java.util.HashMap

import sbt.io.IO

import scala.util.control.NonFatal

trait AbstractClassLoaderCache extends AutoCloseable {
  def commonParent: ClassLoader
  def apply(files: List[File]): ClassLoader

  /**
   * Returns a ClassLoader, as created by `mkLoader`.
   *
   * The returned ClassLoader may be cached from a previous call if the last modified time of all `files` is unchanged.
   * This method is thread-safe.
   */
  def cachedCustomClassloader(
      files: List[File],
      mkLoader: () => ClassLoader
  ): ClassLoader
}
final class ClassLoaderCache(private val abstractClassLoaderCache: AbstractClassLoaderCache)
    extends AutoCloseable {
  def this(commonParent: ClassLoader) = this(new ClassLoaderCacheImpl(commonParent))
  def commonParent: ClassLoader = abstractClassLoaderCache.commonParent
  override def close(): Unit = abstractClassLoaderCache.close()
  def apply(files: List[File]): ClassLoader = abstractClassLoaderCache.apply(files)

  /**
   * Returns a ClassLoader, as created by `mkLoader`.
   *
   * The returned ClassLoader may be cached from a previous call if the last modified time of all `files` is unchanged.
   * This method is thread-safe.
   */
  def cachedCustomClassloader(
      files: List[File],
      mkLoader: () => ClassLoader
  ): ClassLoader = abstractClassLoaderCache.cachedCustomClassloader(files, mkLoader)
}

private final class ClassLoaderCacheImpl(val commonParent: ClassLoader)
    extends AbstractClassLoaderCache {
  private[this] val delegate =
    new HashMap[List[File], Reference[CachedClassLoader]]

  /**
   * Returns a ClassLoader with `commonParent` as a parent and that will load classes from classpath `files`.
   * The returned ClassLoader may be cached from a previous call if the last modified time of all `files` is unchanged.
   * This method is thread-safe.
   */
  def apply(files: List[File]): ClassLoader = synchronized {
    cachedCustomClassloader(
      files,
      () => new URLClassLoader(files.map(_.toURI.toURL).toArray, commonParent)
    )
  }

  /**
   * Returns a ClassLoader, as created by `mkLoader`.
   *
   * The returned ClassLoader may be cached from a previous call if the last modified time of all `files` is unchanged.
   * This method is thread-safe.
   */
  def cachedCustomClassloader(
      files: List[File],
      mkLoader: () => ClassLoader
  ): ClassLoader =
    synchronized {
      val tstamps = files.map(IO.getModifiedTimeOrZero)
      getFromReference(files, tstamps, delegate.get(files), mkLoader)
    }

  override def close(): Unit = {
    delegate.values.forEach(v => Option(v.get).foreach(_.close()))
    delegate.clear()
  }

  private[this] def getFromReference(
      files: List[File],
      stamps: List[Long],
      existingRef: Reference[CachedClassLoader],
      mkLoader: () => ClassLoader
  ) =
    if (existingRef eq null)
      newEntry(files, stamps, mkLoader)
    else
      get(files, stamps, existingRef.get, mkLoader)

  private[this] def get(
      files: List[File],
      stamps: List[Long],
      existing: CachedClassLoader,
      mkLoader: () => ClassLoader
  ): ClassLoader =
    if (existing == null || stamps != existing.timestamps) {
      newEntry(files, stamps, mkLoader)
    } else
      existing.loader

  private[this] def newEntry(
      files: List[File],
      stamps: List[Long],
      mkLoader: () => ClassLoader
  ): ClassLoader = {
    val loader = mkLoader()
    delegate.put(
      files,
      new SoftReference(new CachedClassLoader(loader, files, stamps))
    )
    loader
  }
}
private[sbt] final class CachedClassLoader(
    val loader: ClassLoader,
    val files: List[File],
    val timestamps: List[Long]
) extends AutoCloseable {
  override def close(): Unit = loader match {
    case a: AutoCloseable =>
      try a.close()
      catch { case NonFatal(e) => e.printStackTrace() }
    case _ =>
  }
}
