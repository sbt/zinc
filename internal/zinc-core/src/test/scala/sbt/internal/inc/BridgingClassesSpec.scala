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
import xsbti.VirtualFileRef
import xsbti.api.{ DependencyContext, InternalDependency }
import xsbti.api.DependencyContext.{ DependencyByInheritance, DependencyByMemberRef }

object BridgingClassesSpec extends Properties:
  override def tests: List[Test] = List(
    example("find the unchanged class between two changed classes (sbt/zinc#476)", between),
    example("find every unchanged class on a longer path", longerPath),
    example("return nothing when no path leads back to a changed class", noPath),
    example("follow inheritance edges", inheritance),
  )

  private val src = VirtualFileRef.of("Src.scala")

  private def relations(deps: (String, String, DependencyContext)*): Relations =
    Relations.empty.addInternalSrcDeps(
      src,
      deps.map { case (from, to, ctx) => InternalDependency.of(from, to, ctx) }
    )

  private def bridging(rel: Relations, invalidated: String*): Set[String] =
    IncrementalCommon.bridgingClasses(rel, invalidated.toSet)

  private def between: Result =
    val rel = relations(
      ("Bar", "Foo", DependencyByMemberRef),
      ("Foo", "Provider", DependencyByMemberRef),
      ("Foo", "Providers", DependencyByMemberRef),
      ("Foo", "A", DependencyByMemberRef),
      ("Providers", "Provider", DependencyByMemberRef),
    )
    bridging(rel, "Bar", "Provider", "Providers") ==== Set("Foo")

  private def longerPath: Result =
    val rel = relations(
      ("Z", "Y2", DependencyByMemberRef),
      ("Y2", "Y1", DependencyByMemberRef),
      ("Y1", "X", DependencyByMemberRef),
      ("Unrelated", "X", DependencyByMemberRef),
    )
    bridging(rel, "Z", "X") ==== Set("Y1", "Y2")

  private def noPath: Result =
    val rel = relations(
      ("Dependent", "X", DependencyByMemberRef),
      ("X", "Upstream", DependencyByMemberRef),
    )
    Result.all(List(bridging(rel, "X") ==== Set.empty, bridging(rel) ==== Set.empty))

  private def inheritance: Result =
    val rel = relations(
      ("B", "A", DependencyByInheritance),
      ("Test", "B", DependencyByMemberRef),
    )
    bridging(rel, "A", "Test") ==== Set("B")
end BridgingClassesSpec
