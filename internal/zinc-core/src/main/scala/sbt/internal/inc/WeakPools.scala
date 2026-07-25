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

import java.lang.ref.{ ReferenceQueue, WeakReference }
import java.util.concurrent.ConcurrentHashMap

import scala.annotation.tailrec

/**
 * Canonicalizing pool: `intern` returns one instance per distinct value
 * (`equals`/`hashCode`), and holds it only through a weak reference, so a value
 * becomes collectable as soon as the last caller drops it. Dead entries are
 * expunged by the next pool operation.
 */
private[inc] final class WeakInterner[A <: AnyRef] {
  private val stale = new ReferenceQueue[A]
  private val pool = new ConcurrentHashMap[WeakValue[A], WeakValue[A]]

  def intern(a: A): A = {
    expunge()
    val candidate = new WeakValue(a, stale)
    @tailrec def publish(): A = pool.putIfAbsent(candidate, candidate) match {
      case null => a
      case existing =>
        existing.get match {
          case null => // collected since it matched: drop the dead entry and retry
            pool.remove(existing, existing)
            publish()
          case canonical =>
            candidate.clear() // never enqueue a reference that was not pooled
            canonical
        }
    }
    publish()
  }

  @tailrec private def expunge(): Unit = stale.poll() match {
    case null => ()
    case dead =>
      pool.remove(dead, dead)
      expunge()
  }
}

/**
 * Map that holds its values only through weak references: an entry disappears
 * once its value is unreachable, releasing the strong reference to its key with
 * it. Unlike an interner it is keyed by something cheaper than the value, so
 * callers can probe the pool before constructing a candidate.
 */
private[inc] final class WeakValuePool[K, V <: AnyRef] {
  private val stale = new ReferenceQueue[V]
  private val pool = new ConcurrentHashMap[K, KeyedWeakValue[K, V]]

  /** The pooled value for `key`, or `null` if there is none. */
  def get(key: K): V = {
    expunge()
    pool.get(key) match {
      case null  => null.asInstanceOf[V]
      case entry => entry.get
    }
  }

  /** Pools `value` under `key`, returning `null`, or the value already pooled. */
  @tailrec def putIfAbsent(key: K, value: V): V = {
    val candidate = new KeyedWeakValue(key, value, stale)
    pool.putIfAbsent(key, candidate) match {
      case null => null.asInstanceOf[V]
      case existing =>
        existing.get match {
          case null => // collected since it was published: replace the dead entry
            pool.remove(key, existing)
            putIfAbsent(key, value)
          case pooled =>
            candidate.clear() // never enqueue a reference that was not pooled
            pooled
        }
    }
  }

  @tailrec private def expunge(): Unit = stale.poll() match {
    case null => ()
    case dead =>
      pool.remove(dead.asInstanceOf[KeyedWeakValue[K, V]].key, dead)
      expunge()
  }
}

/** Weak reference that hashes and compares by the value of its referent. */
private final class WeakValue[A <: AnyRef](a: A, stale: ReferenceQueue[A])
    extends WeakReference[A](a, stale) {
  private val hash: Int = a.hashCode

  override def hashCode(): Int = hash
  override def equals(other: Any): Boolean = other match {
    case that: WeakValue[?] =>
      (this `eq` that) || {
        val value = get
        value != null && value == that.get
      }
    case _ => false
  }
}

/** Weak reference that remembers the key its value was pooled under. */
private final class KeyedWeakValue[K, V <: AnyRef](val key: K, v: V, stale: ReferenceQueue[V])
    extends WeakReference[V](v, stale)
