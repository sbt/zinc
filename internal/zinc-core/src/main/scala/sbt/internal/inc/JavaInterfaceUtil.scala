/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.util.Optional
import sbt.util.InterfaceUtil

import xsbti.T2

object JavaInterfaceUtil {
  private[sbt] implicit class EnrichSbtTuple[T, U](sbtTuple: T2[T, U]) {
    def toScalaTuple: (T, U) = sbtTuple.get1() -> sbtTuple.get2()
  }

  private[sbt] implicit class EnrichOptional[T](optional: Optional[T]) {
    def toOption: Option[T] = InterfaceUtil.toOption(optional)
  }

  private[sbt] implicit class EnrichOption[T](option: Option[T]) {
    def toOptional: Optional[T] = InterfaceUtil.toOptional(option)
  }
}
