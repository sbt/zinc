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

package xsbt.api

import xsbti.UseScope
import xsbti.api.Definition
import xsbti.api.DefinitionType
import xsbti.api.ClassLike
import xsbti.api.NameHash

/**
 * A class that computes hashes for each group of definitions grouped by a simple name.
 *
 * See `nameHashes` method for details.
 */
class NameHashing(optimizedSealed: Boolean) {

  import NameHashing._

  // Reused across name groups and classes, which makes an instance unsafe to share between threads.
  private[this] val hasher = HashAPI.forNameHashing(includeSealedChildren = !optimizedSealed)
  private[this] lazy val patMatHasher = HashAPI.forNameHashing(includeSealedChildren = true)

  /**
   * This method takes an API representation and extracts a flat collection of all
   * definitions contained in that API representation. Then it groups definition
   * by a simple name. Lastly, it computes a hash sum of all definitions in a single
   * group.
   *
   * NOTE: The hashing sum used for hashing a group of definition is insensitive
   * to order of definitions.
   */
  def nameHashes(classApi: ClassLike): Array[NameHash] = {
    val defs = publicDefs(classApi)
    val locationHash = Location(classApi.name, NameType(classApi.definitionType)).hashCode
    val out = Array.newBuilder[NameHash]
    val (regularDefs, implicitDefs) = defs.partition(d => !d.modifiers.isImplicit)
    addNameHashes(regularDefs, locationHash, hasher, UseScope.Default, out)
    addNameHashes(implicitDefs, locationHash, hasher, UseScope.Implicit, out)
    if (optimizedSealed) {
      val sealedClasses = defs.filter {
        case classLike: ClassLike => classLike.modifiers().isSealed
        case _                    => false
      }
      if (sealedClasses.nonEmpty) {
        addNameHashes(sealedClasses, locationHash, patMatHasher, UseScope.PatMatTarget, out)
      }
    }
    out.result()
  }

  /**
   * Adds a name hash for each group of definitions in `defs` with the same local name.
   *
   * Each group is hashed in the order its definitions appear in `defs`, which matters as hashing
   * one definition can record a visited class or structure that the next one reuses.
   */
  private def addNameHashes(
      defs: Array[Definition],
      locationHash: Int,
      hasher: HashAPI,
      scope: UseScope,
      out: scala.collection.mutable.ArrayBuilder[NameHash]
  ): Unit = {
    val groups = StableGroups(defs, (d: Definition) => localName(d.name))
    var g = 0
    while (g < groups.size) {
      hasher.reset()
      hasher.hashDefinitionsWithExtraHash(
        groups.elements,
        groups.from(g),
        groups.until(g),
        locationHash
      )
      out += NameHash.of(groups.key(g), scope, hasher.finalizeHash)
      g += 1
    }
  }

  /**
   * A visitor that visits given API object and extracts all nested public
   * definitions it finds. The extracted definitions have Location attached
   * to them which identifies API object's location.
   *
   * The returned location is basically a path to a definition that contains
   * the located definition. For example, if we have:
   *
   * object Foo {
   *   class Bar { def abc: Int }
   * }
   *
   * then location of `abc` is Seq((TermName, Foo), (TypeName, Bar))
   */
  private class ExtractPublicDefinitions extends Visit {
    val defs = scala.collection.mutable.Buffer[Definition]()
    // if the definition is private, we do not visit because we do
    // not want to include any private members or its children
    override def visitDefinition(d: Definition): Unit =
      if (d.isInstanceOf[ClassLike] || APIUtil.isNonPrivate(d)) {
        defs += d
        super.visitDefinition(d)
      }
  }

  private def publicDefs(c: ClassLike): Array[Definition] = {
    val visitor = new ExtractPublicDefinitions
    visitor.visitAPI(c)
    visitor.defs.toArray
  }

  private def localName(name: String): String = {
    // when there's no dot in name `lastIndexOf` returns -1 so we handle
    // that case properly
    val index = name.lastIndexOf('.') + 1
    name.substring(index)
  }

}

object NameHashing {
  def merge(nm1: Array[NameHash], nm2: Array[NameHash]): Array[NameHash] = {
    import scala.collection.mutable.Map
    val m: Map[(String, UseScope), Int] = Map.empty
    nm1.foreach(nh => m += (nh.name, nh.scope) -> nh.hash)
    for (nh <- nm2) {
      val key = (nh.name, nh.scope())
      m.get(key) match {
        case None               => m(key) = nh.hash
        case Some(existingHash) =>
          // combine hashes without taking an order into account
          m(key) = Set(existingHash, nh.hash).hashCode()
      }
    }
    m.map { case ((name, scope), hash) => NameHash.of(name, scope, hash) }.toArray
  }

  private case class LocatedDefinition(location: Location, definition: Definition)

  /**
   * Location is the fully qualified name of a class together with `nameType`
   * that distinguishes between type names and term names. For example:
   *
   * class Foo {
   *   object Bar
   * }
   *
   * The location pointing at Bar will be expressed as Location("Foo.Bar", TermName).
   * Note that `Location` tracks only whether the last segment in a fully qualified
   * name is a TermName or TypeName.
   */
  private case class Location(className: String, nameType: NameType)
  private case class Selector(name: String, nameType: NameType)
  private sealed trait NameType
  private object NameType {
    import DefinitionType._
    def apply(dt: DefinitionType): NameType = dt match {
      case Trait | ClassDef       => TypeName
      case Module | PackageModule => TermName
    }
  }
  private case object TermName extends NameType
  private case object TypeName extends NameType
}
