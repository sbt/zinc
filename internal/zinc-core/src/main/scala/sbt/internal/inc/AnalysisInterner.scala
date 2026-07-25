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

import java.util.{ EnumSet => JEnumSet }

import xsbti.UseScope

/**
 * The process-wide canonicalizer for strings and `UsedName`s: fresh
 * (compiler-produced) and persisted (deserialized) analyses both route through
 * it, so co-resident analyses share a single instance per distinct value.
 *
 * Strings are canonicalized through a weak interner: canonical instances are
 * held only through weak references, so once every analysis referencing a
 * string is released the canonical instance becomes eligible for GC.
 *
 * `UsedName`s are canonicalized without constructing a candidate first: a
 * `UsedName` is fully determined by its (already canonical) name string and one
 * of the eight possible `UseScope` combinations, so each combination gets a
 * weak-valued name-keyed pool. A pool entry is removed once the canonical
 * `UsedName` is no longer referenced by any analysis. On a pool hit no
 * `UsedName` is constructed.
 *
 * Both pools make interning leak-free across the lifetime of a long-running
 * build server. Benchmarks that need an uninterned baseline compare against a
 * stock zinc checkout; production code always interns.
 */
object AnalysisInterner {

  private[inc] final val DEFAULT_SCOPE = 1
  private[inc] final val IMPLICIT_SCOPE = 2
  private[inc] final val PAT_MAT_TARGET_SCOPE = 4
  private final val SCOPE_COMBINATIONS = 8 // every subset of the three scopes above

  private val stringPool = new WeakInterner[String]

  // One weak-valued pool per UseScope combination, keyed by the canonical name.
  private val usedNamePools: Array[WeakValuePool[String, UsedName]] =
    Array.fill(SCOPE_COMBINATIONS)(new WeakValuePool[String, UsedName])

  def internString(s: String): String = stringPool.intern(s)

  /**
   * The canonical `UsedName` for `name` used in the scope combination
   * `scopeBits`, an or-ing of `DEFAULT_SCOPE`, `IMPLICIT_SCOPE` and
   * `PAT_MAT_TARGET_SCOPE`. Constructs a `UsedName` only when the value is not
   * pooled yet.
   */
  def usedName(name: String, scopeBits: Int): UsedName = {
    // UsedName stores the escaped name, so the pool must be probed with it too;
    // escaping is a no-op unless the name contains control characters.
    val escaped = UsedName.escapeControlChars(name)
    val pool = usedNamePools(scopeBits)
    val existing = pool.get(escaped)
    if (existing != null) existing
    else {
      val fresh = UsedName.make(escaped, scopeSets(scopeBits))
      val prev = pool.putIfAbsent(fresh.name, fresh)
      if (prev == null) fresh else prev
    }
  }

  /**
   * The eight possible scope sets, indexed by scope bits. Shared by every
   * UsedName and treated as immutable -- they must never be mutated.
   */
  private[inc] val scopeSets: Array[JEnumSet[UseScope]] =
    Array.tabulate(SCOPE_COMBINATIONS) { bits =>
      val scopes = JEnumSet.noneOf(classOf[UseScope])
      if ((bits & DEFAULT_SCOPE) != 0) scopes.add(UseScope.Default)
      if ((bits & IMPLICIT_SCOPE) != 0) scopes.add(UseScope.Implicit)
      if ((bits & PAT_MAT_TARGET_SCOPE) != 0) scopes.add(UseScope.PatMatTarget)
      scopes
    }

  /** The inverse of `scopeSets`: also the encoding used by the persisted format. */
  private[inc] def scopeBits(scopes: JEnumSet[UseScope]): Int =
    (if (scopes.contains(UseScope.Default)) DEFAULT_SCOPE else 0) |
      (if (scopes.contains(UseScope.Implicit)) IMPLICIT_SCOPE else 0) |
      (if (scopes.contains(UseScope.PatMatTarget)) PAT_MAT_TARGET_SCOPE else 0)
}
