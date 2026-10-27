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
import scala.jdk.CollectionConverters.*
import xsbti.UseScope

object AnalysisInternerSpec extends Properties:
  override def tests: List[Test] = List(
    property("canonicalize equal strings to one instance", propInternString),
    example("return one UsedName instance per (name, scope)", usedNameHit),
    example("distinguish scope combinations for the same name", scopeCombinations),
    example("pool names containing control characters under their escaped form", escaped),
    property("return one UsedName per name and scope combination", propUsedName),
    property("keep scope combinations of a name apart", propScopesApart),
    example("converge on one canonical instance under concurrent interning", concurrent),
    example("release canonical instances once no analysis references them", release),
    example("keep pooled values while an analysis still references them", keep),
  )

  private val genName: Gen[String] =
    Gen.string(Gen.frequency1(9 -> Gen.alphaNum, 1 -> Gen.constant('\n')), Range.linear(1, 16))

  private val genScopeBits: Gen[Int] = Gen.int(Range.constant(0, 7))

  private def expectedScopes(bits: Int): Set[UseScope] =
    Set(
      Option.when((bits & 1) != 0)(UseScope.Default),
      Option.when((bits & 2) != 0)(UseScope.Implicit),
      Option.when((bits & 4) != 0)(UseScope.PatMatTarget),
    ).flatten

  def propInternString: Property =
    for s <- Gen.string(Gen.unicode, Range.linear(0, 32)).forAll
    yield
      val s1 = new String(s) // force distinct heap instances
      val s2 = new String(s)
      Result.assert(AnalysisInterner.internString(s1) eq AnalysisInterner.internString(s2))

  def propUsedName: Property =
    for
      name <- genName.forAll
      bits <- genScopeBits.forAll
    yield
      val a = AnalysisInterner.usedName(name, bits)
      val b = AnalysisInterner.usedName(new String(name), bits)
      Result.all(
        List(
          Result.assert(a eq b),
          a.name ==== UsedName(name).name,
          a.scopes.asScala.toSet ==== expectedScopes(bits),
        )
      )

  def propScopesApart: Property =
    for
      name <- genName.forAll
      bits1 <- genScopeBits.forAll
      bits2 <- genScopeBits.filter(_ != bits1).forAll
    yield Result.assert(AnalysisInterner.usedName(name, bits1) ne
      AnalysisInterner.usedName(name, bits2))

  private def usedNameHit: Result =
    val a = AnalysisInterner.usedName("map", 1)
    val b = AnalysisInterner.usedName(new String("map"), 1)
    Result.all(
      List(
        Result.assert(a eq b),
        a.name ==== "map",
        Result.assert(a.scopes.contains(UseScope.Default)),
        Result.assert(!a.scopes.contains(UseScope.Implicit)),
      )
    )

  private def scopeCombinations: Result =
    val default = AnalysisInterner.usedName("map", 1)
    val implicitScope = AnalysisInterner.usedName("map", 2)
    val patMat = AnalysisInterner.usedName("map", 4)
    Result.all(
      List(
        Result.assert(default ne implicitScope),
        Result.assert(default ne patMat),
        Result.assert(implicitScope.scopes.contains(UseScope.Implicit)),
        Result.assert(patMat.scopes.contains(UseScope.PatMatTarget)),
      )
    )

  private def escaped: Result =
    val a = AnalysisInterner.usedName("weird\nname", 1)
    val b = AnalysisInterner.usedName("weird\nname", 1)
    Result.all(
      List(
        Result.assert(a eq b),
        a.name ==== "weird♨0Aname" // stored escaped, exactly as UsedName.make does
      )
    )

  private def concurrent: Result =
    // Parallel compilation interns from many threads at once. Every thread must
    // observe the same canonical reference, and the pools must never deadlock.
    val threads = 16
    val start = new CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(threads)
    try
      val futures = (1 to threads).map { _ =>
        pool.submit(new Callable[(String, UsedName)]:
          def call(): (String, UsedName) =
            start.await() // release all threads together to maximize contention
            (
              AnalysisInterner.internString(new String("concurrent.value")),
              AnalysisInterner.usedName(new String("concurrent.value"), 5)
            ))
      }
      start.countDown()
      val results = futures.map(_.get(30, TimeUnit.SECONDS)).toList // deadlock => timeout
      Result.all(
        List(
          Result.assert(results.forall(_._1 eq results.head._1)),
          Result.assert(results.forall(_._2 eq results.head._2)),
        )
      )
    finally pool.shutdownNow()
  end concurrent

  private def release: Result =
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
    Result.assert(released).log("weak pools must not retain values after they become unreachable")

  private def keep: Result =
    val held = AnalysisInterner.usedName("held", 1)
    System.gc()
    Thread.sleep(10)
    Result.assert(AnalysisInterner.usedName("held", 1) eq held)
end AnalysisInternerSpec
