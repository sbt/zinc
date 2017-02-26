/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.util

import xsbti.{ Reporter, Logger => xLogger }
import xsbti.compile.{ CachedCompiler, CachedCompilerProvider, GlobalsCache, Output }
import sbt.util.Logger.f0

/**
 * Manage a number of <code>maxInstance</code> of cached Scala compilers.
 * @param maxInstances The maximum number to be cached.
 */
final class CompilerCache(val maxInstances: Int) extends GlobalsCache {
  /** Define a least-recently used cache indexed by a generated key. */
  private[this] val cache = lru[CompilerKey, CachedCompiler](maxInstances)
  private[this] def lru[A, B](max: Int) = {
    new util.LinkedHashMap[A, B](8, 0.75f, true) {
      override def removeEldestEntry(eldest: util.Map.Entry[A, B]): Boolean =
        size > max
    }
  }

  override def apply(
    args: Array[String],
    output: Output,
    forceNew: Boolean,
    c: CachedCompilerProvider,
    log: xLogger,
    reporter: Reporter
  ): CachedCompiler = synchronized {
    val scalaVersion = c.scalaInstance.actualVersion
    val key = CompilerKey(dropSources(args.toList), scalaVersion)
    if (forceNew) cache.remove(key)
    cache.get(key) match {
      case null =>
        log.debug(f0(s"Compiler cache miss. $key "))
        val newCompiler: CachedCompiler =
          c.newCachedCompiler(args, output, log, reporter, !forceNew)
        cache.put(key, newCompiler)
        newCompiler
      case cachedCompiler =>
        val hexHashCode = cachedCompiler.hashCode.toLong.toHexString
        log.debug(f0(s"Compiler cache hit ($hexHashCode). $key"))
        cachedCompiler
    }
  }

  override def clear(): Unit = synchronized { cache.clear() }

  private[this] def dropSources(args: Seq[String]): Seq[String] =
    args.filterNot(arg => arg.endsWith(".scala") || arg.endsWith(".java"))

  private[this] case class CompilerKey(args: Seq[String], scalaVersion: String) {
    override def toString: String =
      s"scala $scalaVersion, args: ${args.mkString(" ")}"
  }
}

/** Define helpers to create compiler caches. */
object CompilerCache {

  /**
   * Return a compiler cache that manages up until <b>5</b> cached
   * Scala compilers, where 5 is the default number.
   */
  def default: GlobalsCache = new CompilerCache(5)

  /**
   * Return a compiler cache that manages up until <code>maxInstances</code>
   * of cached Scala compilers.
   *
   * @param maxInstances Number of maximum Scala compilers cached.
   */
  def apply(maxInstances: Int): GlobalsCache =
    new CompilerCache(maxInstances)

  /**
   * Return a cached compiler
   */
  val fresh: GlobalsCache = new GlobalsCache {
    def clear(): Unit = ()
    def apply(
      args: Array[String],
      output: Output,
      forceNew: Boolean,
      c: CachedCompilerProvider,
      log: xLogger,
      reporter: Reporter
    ): CachedCompiler = c.newCachedCompiler(args, output, log, reporter, false)
  }
}
