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

import xsbti.VirtualFileRef
import xsbti.api.{ DependencyContext, InternalDependency }
import xsbti.api.DependencyContext.{ DependencyByInheritance, DependencyByMemberRef }

class BridgingClassesSpec extends UnitSpec {
  behavior.of("IncrementalCommon.bridgingClasses")

  private val src = VirtualFileRef.of("Src.scala")

  private def relations(deps: (String, String, DependencyContext)*): Relations =
    Relations.empty.addInternalSrcDeps(
      src,
      deps.map { case (from, to, ctx) => InternalDependency.of(from, to, ctx) }
    )

  private def bridging(rel: Relations, invalidated: String*): Set[String] =
    IncrementalCommon.bridgingClasses(rel, invalidated.toSet)

  it should "find the unchanged class between two changed classes (sbt/zinc#476)" in {
    val rel = relations(
      ("Bar", "Foo", DependencyByMemberRef),
      ("Foo", "Provider", DependencyByMemberRef),
      ("Foo", "Providers", DependencyByMemberRef),
      ("Foo", "A", DependencyByMemberRef),
      ("Providers", "Provider", DependencyByMemberRef),
    )
    bridging(rel, "Bar", "Provider", "Providers") shouldBe Set("Foo")
  }

  it should "find every unchanged class on a longer path" in {
    val rel = relations(
      ("Z", "Y2", DependencyByMemberRef),
      ("Y2", "Y1", DependencyByMemberRef),
      ("Y1", "X", DependencyByMemberRef),
      ("Unrelated", "X", DependencyByMemberRef),
    )
    bridging(rel, "Z", "X") shouldBe Set("Y1", "Y2")
  }

  it should "return nothing when no path leads back to a changed class" in {
    val rel = relations(
      ("Dependent", "X", DependencyByMemberRef),
      ("X", "Upstream", DependencyByMemberRef),
    )
    bridging(rel, "X") shouldBe Set.empty
    bridging(rel) shouldBe Set.empty
  }

  it should "follow inheritance edges" in {
    val rel = relations(
      ("B", "A", DependencyByInheritance),
      ("Test", "B", DependencyByMemberRef),
    )
    bridging(rel, "A", "Test") shouldBe Set("B")
  }
}
