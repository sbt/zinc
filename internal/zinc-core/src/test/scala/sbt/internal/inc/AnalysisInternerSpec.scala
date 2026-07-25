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
import xsbti.UseScope

class AnalysisInternerSpec extends UnitSpec {

  behavior of "AnalysisInterner"

  it should "canonicalize equal strings to one instance" in {
    val s1 = new String("com.example.Foo") // force distinct heap instances
    val s2 = new String("com.example.Foo")
    assert(s1 `ne` s2)
    assert(AnalysisInterner.internString(s1) `eq` AnalysisInterner.internString(s2))
  }

  it should "return one UsedName instance per (name, scope) without construction on hit" in {
    val a = AnalysisInterner.usedName("map", 1)
    val b = AnalysisInterner.usedName(new String("map"), 1)
    assert(a `eq` b)
    assert(a.name == "map")
    assert(a.scopes.contains(UseScope.Default))
    assert(!a.scopes.contains(UseScope.Implicit))
  }

  it should "distinguish scope combinations for the same name" in {
    val default = AnalysisInterner.usedName("map", 1)
    val implicitScope = AnalysisInterner.usedName("map", 2)
    val patMat = AnalysisInterner.usedName("map", 4)
    assert(default `ne` implicitScope)
    assert(default `ne` patMat)
    assert(implicitScope.scopes.contains(UseScope.Implicit))
    assert(patMat.scopes.contains(UseScope.PatMatTarget))
  }

  it should "pool names containing control characters under their escaped form" in {
    val a = AnalysisInterner.usedName("weird\nname", 1)
    val b = AnalysisInterner.usedName("weird\nname", 1)
    assert(a `eq` b)
    assert(a.name == "weird♨0Aname") // stored escaped, exactly as UsedName.make does
  }

  it should "converge on one canonical instance under concurrent interning" in {
    // Parallel compilation interns from many threads at once. Every thread must
    // observe the same canonical reference, and the pools must never deadlock.
    val threads = 16
    val start = new CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(threads)
    try {
      val futures = (1 to threads).map { _ =>
        pool.submit(new Callable[(String, UsedName)] {
          def call(): (String, UsedName) = {
            start.await() // release all threads together to maximize contention
            (
              AnalysisInterner.internString(new String("concurrent.value")),
              AnalysisInterner.usedName(new String("concurrent.value"), 5)
            )
          }
        })
      }
      start.countDown()
      val results = futures.map(_.get(30, TimeUnit.SECONDS)).toList // deadlock => timeout
      assert(results.forall(_._1 `eq` results.head._1))
      assert(results.forall(_._2 `eq` results.head._2))
    } finally pool.shutdownNow()
  }

  it should "release canonical instances once no analysis references them" in {
    // The leak-free requirement: canonical instances are held only weakly, so
    // once every analysis referencing a value is dropped it becomes GC-eligible.
    val stringRef =
      new WeakReference[String](AnalysisInterner.internString(new String("ephemeral.str")))
    val usedNameRef =
      new WeakReference[UsedName](AnalysisInterner.usedName("ephemeral.used", 1))
    val released = (1 to 100).exists { _ =>
      System.gc()
      Thread.sleep(10)
      stringRef.get() == null && usedNameRef.get() == null
    }
    assert(released, "weak pools must not retain values after they become unreachable")
  }

  it should "keep pooled values while an analysis still references them" in {
    val held = AnalysisInterner.usedName("held", 1)
    System.gc()
    Thread.sleep(10)
    assert(AnalysisInterner.usedName("held", 1) `eq` held)
  }
}
