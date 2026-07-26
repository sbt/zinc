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

import java.{ util => ju }
import scala.{ collection => sc }
import scala.util.hashing.MurmurHash3
import xsbti.compile.{ UsedName => XUsedName }
import xsbti.UseScope

/**
 * `scopes` must never be mutated after construction: instances may share one
 * scope set (`make` uses the set it is given, and interning aliases equal
 * instances), and the hash below is computed once.
 */
case class UsedName private (name: String, scopes: ju.EnumSet[UseScope]) extends XUsedName {
  override def getName: String = name
  override def getScopes: ju.EnumSet[UseScope] = scopes

  // A canonical (interned) instance is inserted into one name-set per class that
  // uses it, and EnumSet.hashCode iterates its elements; caching makes each
  // re-hash a field read. Fits in the object's existing alignment padding.
  override val hashCode: Int = MurmurHash3.caseClassHash(this)
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

  private[inc] def escapeControlChars(name: String): String = {
    if (name.indexOf('\n') > 0) // optimize for common case to regex overhead
      name.replace("\n", "\u26680A")
    else
      name
  }
}

sealed abstract class UsedNames private {
  def isEmpty: Boolean
  def toMultiMap: sc.Map[String, sc.Set[UsedName]]

  def ++(other: UsedNames): UsedNames
  def --(classes: Iterable[String]): UsedNames
  def iterator: Iterator[(String, sc.Set[UsedName])]

  def hasAffectedNames(modifiedNames: ModifiedNames, from: String): Boolean
  def affectedNames(modifiedNames: ModifiedNames, from: String): String
}

object UsedNames {
  // def fromJavaMap(map: ju.Map[String, Schema.UsedNames]) = JavaUsedNames(map)
  def fromMultiMap(map: sc.Map[String, sc.Set[UsedName]]) = ScalaUsedNames(map)

  final case class ScalaUsedNames(map: sc.Map[String, sc.Set[UsedName]]) extends UsedNames {
    def isEmpty = map.isEmpty
    def toMultiMap = map
    def ++(other: UsedNames) = fromMultiMap(map ++ other.iterator)
    def --(classes: Iterable[String]) = fromMultiMap(map.toMap -- classes)
    def iterator = map.iterator
    def hasAffectedNames(modifiedNames: ModifiedNames, from: String): Boolean =
      map(from).iterator.exists(modifiedNames.isModified)
    def affectedNames(modifiedNames: ModifiedNames, from: String): String =
      map(from).iterator.filter(modifiedNames.isModified).mkString(", ")
  }
}
