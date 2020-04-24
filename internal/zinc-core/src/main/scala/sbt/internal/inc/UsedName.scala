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

import xsbti.compile.{ UsedName => XUsedName }
import xsbti.UseScope

case class UsedName private (name: String, scopes: java.util.EnumSet[UseScope]) extends XUsedName {
  override def getName: String = name
  override def getScopes: java.util.EnumSet[UseScope] = scopes
}

object UsedName {

  def apply(name: String, scopes: Iterable[UseScope] = Nil): UsedName = {
    val useScopes = java.util.EnumSet.noneOf(classOf[UseScope])
    scopes.foreach(useScopes.add)
    UsedName.make(name, useScopes)
  }

  def make(name: String, useScopes: java.util.EnumSet[UseScope]): UsedName = {
    val escapedName = escapeControlChars(name)
    new UsedName(escapedName, useScopes)
  }

  private def escapeControlChars(name: String) = {
    if (name.indexOf('\n') > 0) // optimize for common case to regex overhead
      name.replaceAllLiterally("\n", "\u26680A")
    else
      name
  }
}
