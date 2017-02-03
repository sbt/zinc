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