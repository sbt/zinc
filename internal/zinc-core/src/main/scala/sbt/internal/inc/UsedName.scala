/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.util

import xsbti.UseScope

case class UsedName(name: String, scopes: util.EnumSet[UseScope])

object UsedName {
  def apply(name: String, scopes: Iterable[UseScope] = Nil): UsedName = {
    val useScopes = util.EnumSet.noneOf(classOf[UseScope])
    scopes.foreach(useScopes.add)
    UsedName(name, useScopes)
  }
}