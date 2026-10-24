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

package sbt.inc.binary.converter

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.binary.converters.InternalApiProxy

object InternalApiProxySpecification extends Properties:
  override def tests: List[Test] = List(
    example("should create Modifiers from tags", fromZero),
    property("should create Modifiers from any tags", propFromTags),
  )

  private def fromZero: Result =
    val modifiers = InternalApiProxy.Modifiers(0)
    Result.all(
      List(
        Result.assert(!modifiers.isAbstract),
        Result.assert(!modifiers.isFinal),
        Result.assert(!modifiers.isImplicit),
        Result.assert(!modifiers.isLazy),
        Result.assert(!modifiers.isMacro),
        Result.assert(!modifiers.isOverride),
        Result.assert(!modifiers.isSealed),
        Result.assert(!modifiers.isSuperAccessor),
      )
    )

  def propFromTags: Property =
    for flags <- Gen.int(Range.constant(0, 255)).forAll
    yield
      def bit(i: Int) = (flags & (1 << i)) != 0
      val modifiers = InternalApiProxy.Modifiers(flags)
      modifiers ==== new xsbti.api.Modifiers(
        bit(0),
        bit(1),
        bit(2),
        bit(3),
        bit(4),
        bit(5),
        bit(6),
        bit(7)
      )
end InternalApiProxySpecification
