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

import java.{ util => ju }

import scala.{ collection => sc }
import xsbti.compile.{ UsedName => XUsedName }
import xsbti.UseScope

case class UsedName private (name: String, scopes: ju.EnumSet[UseScope]) extends XUsedName {
  override def getName: String = name
  override def getScopes: ju.EnumSet[UseScope] = scopes
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

sealed abstract class UsedNames private {
  def isEmpty: Boolean
  def toMultiMap: sc.Map[String, sc.Set[UsedName]]

  def ++(other: UsedNames): UsedNames
  def --(classes: Iterable[String]): UsedNames
  def iterator: Iterator[(String, sc.Set[UsedName])]

  def affectedNames(modifiedNames: ModifiedNames, from: String): String
}

object UsedNames {
  def fromJavaMap(map: ju.Map[String, Schema.UsedNames]) = JavaUsedNames(map)
  def fromMultiMap(map: sc.Map[String, sc.Set[UsedName]]) = ScalaUsedNames(map)

  final case class ScalaUsedNames(map: sc.Map[String, sc.Set[UsedName]]) extends UsedNames {
    def isEmpty = map.isEmpty
    def toMultiMap = map
    def ++(other: UsedNames) = fromMultiMap(map ++ other.iterator)
    def --(classes: Iterable[String]) = fromMultiMap(map -- classes)
    def iterator = map.iterator
    def affectedNames(modifiedNames: ModifiedNames, from: String): String =
      map(from).iterator.filter(modifiedNames.isModified).mkString(", ")
  }

  final case class JavaUsedNames(map: ju.Map[String, Schema.UsedNames]) extends UsedNames {
    import scala.collection.JavaConverters._

    private def fromUseScope(useScope: Schema.UseScope, id: Int): UseScope = useScope match {
      case Schema.UseScope.DEFAULT  => UseScope.Default
      case Schema.UseScope.IMPLICIT => UseScope.Implicit
      case Schema.UseScope.PATMAT   => UseScope.PatMatTarget
      case Schema.UseScope.UNRECOGNIZED =>
        sys.error(s"Unrecognized ${classOf[Schema.UseScope].getName} with value `$id`.")
    }

    private def fromUsedName(usedName: Schema.UsedName): UsedName = {
      val name = usedName.getName.intern() // ?
      val useScopes = ju.EnumSet.noneOf(classOf[UseScope])
      val len = usedName.getScopesCount
      for (i <- 0 to len - 1)
        useScopes.add(fromUseScope(usedName.getScopes(i), usedName.getScopesValue(i)))
      UsedName.make(name, useScopes)
    }

    private def fromUsedNamesMap(map: ju.Map[String, Schema.UsedNames]) =
      for ((k, used) <- map.asScala)
        yield k -> used.getUsedNamesList.asScala.iterator.map(fromUsedName).toSet

    lazy val toMultiMap: sc.Map[String, sc.Set[UsedName]] = fromUsedNamesMap(map)
    private lazy val convert: UsedNames = fromMultiMap(toMultiMap)

    def isEmpty = map.isEmpty
    def ++(other: UsedNames) = convert ++ other
    def --(classes: Iterable[String]) = convert -- classes
    def iterator = convert.iterator

    def affectedNames(modifiedNames: ModifiedNames, from: String): String = {
      val b = new StringBuilder()
      val usedNames = map.get(from)
      var first = true
      var i = 0
      val n = usedNames.getUsedNamesCount
      while (i < n) {
        val usedName = usedNames.getUsedNames(i)
        val name = usedName.getName
        var i2 = 0
        val n2 = usedName.getScopesCount
        while (i2 < n2) {
          val scope = fromUseScope(usedName.getScopes(i2), usedName.getScopesValue(i2))
          if (modifiedNames.isModifiedRaw(name, scope)) {
            if (first) first = false else b.append(", ")
            b.append(name)
          }
          i2 += 1
        }
        i += 1
      }
      b.toString
    }
  }
}
