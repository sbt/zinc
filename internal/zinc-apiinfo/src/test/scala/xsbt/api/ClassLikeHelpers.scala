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

package xsbt.api

import xsbti.api._

object ClassLikeHelpers {
  def lzy[T](x: T): Lazy[T] = new Lazy[T] { def get: T = x }

  def simpleStructure(defs: ClassDefinition*) =
    Structure.of(lzy(Array.empty[Type]), lzy(defs.toArray), emptyMembers)

  def simpleTrait(name: String, defs: List[ClassDefinition]): ClassLike = {
    val structure = simpleStructure(defs: _*)
    simpleClassLike(name, structure, dt = DefinitionType.Trait)
  }

  def simpleClass(name: String, defs: ClassDefinition*): ClassLike = {
    val structure = simpleStructure(defs: _*)
    simpleClassLike(name, structure)
  }

  def simpleObject(name: String, defs: ClassDefinition*): ClassLike = {
    val structure = simpleStructure(defs: _*)
    simpleClassLike(name, structure, dt = DefinitionType.Module)
  }

  def simpleClassLikeDef(
      name: String,
      dt: DefinitionType = DefinitionType.ClassDef,
      access: Access = publicAccess
  ): ClassLikeDef = {
    ClassLikeDef.of(name, access, defaultMods, Array.empty, Array.empty, dt)
  }

  def simpleClassLike(
      name: String,
      structure: Structure,
      dt: DefinitionType = DefinitionType.ClassDef,
      topLevel: Boolean = true,
      access: Access = publicAccess
  ): ClassLike = {
    // FORMAT: OFF
    ClassLike.of(name, access, defaultMods, Array.empty, dt, lzy(emptyType), lzy(structure), Array.empty, Array.empty, topLevel, Array.empty)
    // FORMAT: ON
  }

  final val emptyType = EmptyType.of()
  final val intTpe = Projection.of(emptyType, "Int")
  final val strTpe = Projection.of(emptyType, "String")
  final val publicAccess = Public.of()
  final val privateAccess = Private.of(Unqualified.of())
  final val emptyMembers = lzy(Array.empty[ClassDefinition])
  final val defaultMods =
    new Modifiers(false, false, false, false, false, false, false, false)
}
