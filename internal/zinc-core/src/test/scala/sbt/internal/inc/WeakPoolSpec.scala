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

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.lang.ref.WeakReference
import java.util.concurrent.{ Callable, CountDownLatch, Executors, TimeUnit }

object WeakPoolSpec extends Properties:
  override def tests: List[Test] = List(
    example("WeakInterner should release canonical instances once they are unreachable", release),
    example("WeakInterner should converge on one instance under concurrent interning", concurrent),
    property("WeakInterner should return one canonical instance per equal value", propCanonical),
    property("WeakInterner should keep values that are not equal apart", propApart),
    example("WeakValuePool should return null for an absent key", absentKey),
    example(
      "WeakValuePool should drop the entry, including its key, once the value is unreachable",
      dropEntry
    ),
    property("WeakValuePool should keep the first value published for a key", propFirstValue),
  )

  /** GC is not obliged to collect on the first attempt, so retry for a while. */
  private def gcUntil(cond: => Boolean): Boolean = (1 to 100).exists { _ =>
    System.gc()
    Thread.sleep(10)
    cond
  }

  private val genValue: Gen[String] = Gen.string(Gen.unicode, Range.linear(0, 32))

  def propCanonical: Property =
    for value <- genValue.forAll
    yield
      val interner = new WeakInterner[String]
      val first = interner.intern(new String(value)) // force distinct heap instances
      val second = interner.intern(new String(value))
      Result.all(List(Result.assert(first eq second), first ==== value))

  def propApart: Property =
    for
      a <- genValue.forAll
      b <- genValue.filter(_ != a).forAll
    yield
      val interner = new WeakInterner[String]
      Result.assert(interner.intern(a) ne interner.intern(b))

  private def release: Result =
    val interner = new WeakInterner[String]
    val ref = new WeakReference(interner.intern(new String("ephemeral")))
    // Any pool operation drains the reference queue; without that the pool would
    // keep growing for the lifetime of a build server.
    Result.assert(gcUntil { interner.intern("probe"); ref.get() == null })

  private def concurrent: Result =
    val interner = new WeakInterner[String]
    val threads = 16
    val start = new CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(threads)
    try
      val futures = (1 to threads).map { _ =>
        executor.submit(new Callable[String]:
          def call(): String =
            start.await() // release all threads together to maximize contention
            interner.intern(new String("contended")))
      }
      start.countDown()
      val results = futures.map(_.get(30, TimeUnit.SECONDS)).toList // deadlock => timeout
      Result.assert(results.forall(_ eq results.head))
    finally executor.shutdownNow()

  private def absentKey: Result =
    val pool = new WeakValuePool[String, StringBuilder]
    Result.assert(pool.get("absent") == null)

  def propFirstValue: Property =
    for
      key <- genValue.forAll
      value <- genValue.forAll
    yield
      val pool = new WeakValuePool[String, StringBuilder]
      val first = new StringBuilder(value)
      Result.all(
        List(
          Result.assert(pool.putIfAbsent(key, first) == null),
          Result.assert(pool.putIfAbsent(new String(key), new StringBuilder(value)) eq first),
          Result.assert(pool.get(new String(key)) eq first),
        )
      )

  private def dropEntry: Result =
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
    Result.assert(gcUntil { pool.get("other"); keyRef.get() == null && valueRef.get() == null })
end WeakPoolSpec
