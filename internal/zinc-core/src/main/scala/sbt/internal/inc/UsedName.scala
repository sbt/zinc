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
import xsbti.{ NameKind, UseScope }

/**
 * `scopes` and `ownerKinds` must never be mutated after construction: instances may
 * share one set (`make` uses the sets it is given, and interning aliases equal
 * instances), and the hash below is computed once.
 *
 * `ownerKinds` says whether the name refers to a member of a class (Type) or of an
 * object (Term); both when the use does not tell.
 */
case class UsedName private (
    name: String,
    scopes: ju.EnumSet[UseScope],
    ownerKinds: ju.EnumSet[NameKind]
) extends XUsedName {
  override def getName: String = name
  override def getScopes: ju.EnumSet[UseScope] = scopes
  override def getOwnerKinds: ju.EnumSet[NameKind] = ownerKinds

  // A canonical (interned) instance is inserted into one name-set per class that
  // uses it, and EnumSet.hashCode iterates its elements; caching makes each
  // re-hash a field read. Fits in the object's existing alignment padding.
  override val hashCode: Int = MurmurHash3.caseClassHash(this)
}

object UsedName {
  def apply(
      name: String,
      scopes: Iterable[UseScope] = Nil,
      ownerKinds: Iterable[NameKind] = NameKind.values.toList
  ): UsedName = {
    val useScopes = java.util.EnumSet.noneOf(classOf[UseScope])
    scopes.foreach(useScopes.add)
    val kinds = java.util.EnumSet.noneOf(classOf[NameKind])
    ownerKinds.foreach(kinds.add)
    UsedName.make(name, useScopes, kinds)
  }

  def make(name: String, useScopes: java.util.EnumSet[UseScope]): UsedName =
    make(name, useScopes, java.util.EnumSet.allOf(classOf[NameKind]))

  def make(
      name: String,
      useScopes: java.util.EnumSet[UseScope],
      ownerKinds: java.util.EnumSet[NameKind]
  ): UsedName = {
    require(!ownerKinds.isEmpty, s"no owner kind for used name $name")
    val escapedName = escapeControlChars(name)
    new UsedName(escapedName, useScopes, ownerKinds)
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
      InvalidationLog
        .formatUsedNames(map(from).filter(modifiedNames.isModified))
        .mkString("\n")
  }
}
