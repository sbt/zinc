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

import java.lang.ref.WeakReference
import java.util.concurrent.{ Callable, CountDownLatch, Executors, TimeUnit }

class WeakPoolSpec extends UnitSpec {

  /** GC is not obliged to collect on the first attempt, so retry for a while. */
  private def gcUntil(cond: => Boolean): Boolean =
    (1 to 100).exists { _ =>
      System.gc()
      Thread.sleep(10)
      cond
    }

  behavior of "WeakInterner"

  it should "return one canonical instance per equal value" in {
    val interner = new WeakInterner[String]
    val first = interner.intern(new String("value")) // force distinct heap instances
    val second = interner.intern(new String("value"))
    assert(first `eq` second)
    assert(first == "value")
  }

  it should "keep values that are not equal apart" in {
    val interner = new WeakInterner[String]
    assert(interner.intern("a") `ne` interner.intern("b"))
  }

  it should "release canonical instances once they are unreachable" in {
    val interner = new WeakInterner[String]
    val ref = new WeakReference(interner.intern(new String("ephemeral")))
    // Any pool operation drains the reference queue; without that the pool would
    // keep growing for the lifetime of a build server.
    assert(gcUntil { interner.intern("probe"); ref.get() == null })
  }

  it should "converge on one instance under concurrent interning" in {
    val interner = new WeakInterner[String]
    val threads = 16
    val start = new CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(threads)
    try {
      val futures = (1 to threads).map { _ =>
        executor.submit(new Callable[String] {
          def call(): String = {
            start.await() // release all threads together to maximize contention
            interner.intern(new String("contended"))
          }
        })
      }
      start.countDown()
      val results = futures.map(_.get(30, TimeUnit.SECONDS)).toList // deadlock => timeout
      assert(results.forall(_ `eq` results.head))
    } finally executor.shutdownNow()
  }

  behavior of "WeakValuePool"

  it should "return null for an absent key" in {
    val pool = new WeakValuePool[String, StringBuilder]
    assert(pool.get("absent") == null)
  }

  it should "keep the first value published for a key" in {
    val pool = new WeakValuePool[String, StringBuilder]
    val first = new StringBuilder("v")
    assert(pool.putIfAbsent("k", first) == null)
    assert(pool.putIfAbsent("k", new StringBuilder("v")) `eq` first)
    assert(pool.get("k") `eq` first)
  }

  it should "drop the entry, including its key, once the value is unreachable" in {
    val pool = new WeakValuePool[String, StringBuilder]
    var key: String = new String("transient.key") // a literal would live in the string table
    var value: StringBuilder = new StringBuilder("transient.value")
    pool.putIfAbsent(key, value)
    val keyRef = new WeakReference(key)
    val valueRef = new WeakReference(value)
    key = null
    value = null
    // The entry holds its key strongly, so releasing the value must release the
    // key with it -- otherwise the pool leaks one key per dead value.
    assert(gcUntil { pool.get("other"); keyRef.get() == null && valueRef.get() == null })
  }
}
