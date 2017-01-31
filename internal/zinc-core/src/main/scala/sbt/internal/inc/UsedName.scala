package sbt.internal.inc

import java.util

import xsbti.UseScope

/**
 * Author: Krzysztof Romanowski
 */
case class UsedName(name: String, scopes: util.EnumSet[UseScope])

object UsedName {
  def forName(name: String) = UsedName(name, util.EnumSet.noneOf(classOf[UseScope]))
  def apply(name: String, nonDefaultScopes: Iterable[UseScope] = Nil): UsedName = {
    val useScopes = util.EnumSet.noneOf(classOf[UseScope])
    nonDefaultScopes.foreach(useScopes.add)
    UsedName(name, useScopes)
  }

  def withDefault(name: String, nonDefaultScopes: Seq[UseScope] = Nil): UsedName =
    apply(name, UseScope.Default +: nonDefaultScopes)
}