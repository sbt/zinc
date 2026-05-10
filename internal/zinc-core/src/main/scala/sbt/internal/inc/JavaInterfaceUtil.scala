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

import java.util.Optional
import sbt.util.InterfaceUtil

import xsbti.T2

object JavaInterfaceUtil {
  private[sbt] implicit class EnrichSbtTuple[T, U](sbtTuple: T2[T, U]) {
    def toScalaTuple: (T, U) = sbtTuple.get1() -> sbtTuple.get2()
  }

  @deprecated("use scala.jdk.OptionConverters instead", "2.0.0-M17")
  private[sbt] class EnrichOptional[T](optional: Optional[T]) {
    def toOption: Option[T] = InterfaceUtil.toOption(optional)
  }

  @deprecated("use scala.jdk.OptionConverters instead", "2.0.0-M17")
  private[sbt] class EnrichOption[T](option: Option[T]) {
    def toOptional: Optional[T] = InterfaceUtil.toOptional(option)
  }
}
