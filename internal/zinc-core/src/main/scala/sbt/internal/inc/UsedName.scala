/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc

import java.util

import xsbti.UseScope

case class UsedName private (name: String, scopes: util.EnumSet[UseScope])

object UsedName {

  def apply(name: String, scopes: Iterable[UseScope] = Nil): UsedName = {
    val escapedName = escapeControlChars(name)
    val useScopes = util.EnumSet.noneOf(classOf[UseScope])
    scopes.foreach(useScopes.add)
    UsedName(escapedName, useScopes)
  }

  private def escapeControlChars(name: String) = {
    if (name.indexOf('\n') > 0) // optimize for common case to regex overhead
      name.replaceAllLiterally("\n", "\u26680A")
    else
      name
  }
}
