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
class NameHashing(optimizedSealed: Boolean):

  import NameHashing.*

  // Reused across name groups and classes, which makes an instance unsafe to share between threads.
  private var hasher: HashAPI = null
  private var patMatHasher: HashAPI = null

  /**
   * This method takes an API representation and extracts a flat collection of all
   * definitions contained in that API representation. Then it groups definition
   * by a simple name. Lastly, it computes a hash sum of all definitions in a single
   * group.
   *
   * NOTE: The hashing sum used for hashing a group of definition is insensitive
   * to order of definitions.
   */
  def nameHashes(classApi: ClassLike): Array[NameHash] =
    val defs = publicDefs(classApi)
    val locationHash = Location(classApi.name, NameType(classApi.definitionType)).hashCode
    val sealedClasses =
      if optimizedSealed then
        defs.collect {
          case classLike: ClassLike if classLike.modifiers().isSealed => classLike: Definition
        }
      else null
    val out = Array.newBuilder[NameHash]
    // A stable sort keeps each group in the order the definitions were found, which matters as
    // hashing one definition can record a visited class or structure that the next one reuses.
    java.util.Arrays.sort(defs, ByImplicitThenLocalName)
    if hasher == null then hasher = HashAPI.forNameHashing(includeSealedChildren = !optimizedSealed)
    addNameHashes(defs, locationHash, hasher, None, out)
    if sealedClasses != null && sealedClasses.nonEmpty then
      java.util.Arrays.sort(sealedClasses, ByLocalName)
      if patMatHasher == null then
        patMatHasher = HashAPI.forNameHashing(includeSealedChildren = true)
      addNameHashes(sealedClasses, locationHash, patMatHasher, Some(UseScope.PatMatTarget), out)
    out.result()
  end nameHashes

  /**
   * Adds a name hash for each run of definitions in `defs` with the same local name and, unless
   * `scope` is given, the same implicitness, which picks the scope.
   */
  private def addNameHashes(
      defs: Array[Definition],
      locationHash: Int,
      hasher: HashAPI,
      scope: Option[UseScope],
      out: scala.collection.mutable.ArrayBuilder[NameHash]
  ): Unit =
    val comparator = if scope.isEmpty then ByImplicitThenLocalName else ByLocalName
    var from = 0
    while from < defs.length do
      var until = from + 1
      while until < defs.length && comparator.compare(defs(from), defs(until)) == 0 do until += 1
      hasher.reset()
      hasher.hashDefinitionsWithExtraHash(defs, from, until, locationHash)
      val useScope = scope.getOrElse(
        if defs(from).modifiers.isImplicit then UseScope.Implicit else UseScope.Default
      )
      out += NameHash.of(localName(defs(from).name), useScope, hasher.finalizeHash)
      from = until

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
  private class ExtractPublicDefinitions extends Visit:
    val defs = scala.collection.mutable.Buffer[Definition]()
    // if the definition is private, we do not visit because we do
    // not want to include any private members or its children
    override def visitDefinition(d: Definition): Unit =
      if d.isInstanceOf[ClassLike] || APIUtil.isNonPrivate(d) then
        defs += d
        super.visitDefinition(d)

  private def publicDefs(c: ClassLike): Array[Definition] =
    val visitor = new ExtractPublicDefinitions
    visitor.visitAPI(c)
    visitor.defs.toArray

  private def localName(name: String): String =
    // when there's no dot in name `lastIndexOf` returns -1 so we handle
    // that case properly
    val index = name.lastIndexOf('.') + 1
    name.substring(index)
end NameHashing

object NameHashing:
  def merge(nm1: Array[NameHash], nm2: Array[NameHash]): Array[NameHash] =
    import scala.collection.mutable.Map
    val m: Map[(String, UseScope), Int] = Map.empty
    nm1.foreach(nh => m += (nh.name, nh.scope) -> nh.hash)
    for nh <- nm2 do
      val key = (nh.name, nh.scope())
      m.get(key) match
        case None               => m(key) = nh.hash
        case Some(existingHash) =>
          // combine hashes without taking an order into account
          m(key) = Set(existingHash, nh.hash).hashCode()
    m.map { case ((name, scope), hash) => NameHash.of(name, scope, hash) }.toArray

  private case class LocatedDefinition(location: Location, definition: Definition)

  /** Orders definitions by the name after the last dot, without allocating that name. */
  private object ByLocalName extends java.util.Comparator[Definition]:
    def compare(x: Definition, y: Definition): Int =
      val a = x.name
      val b = y.name
      val aFrom = a.lastIndexOf('.') + 1
      val bFrom = b.lastIndexOf('.') + 1
      val aLen = a.length - aFrom
      val bLen = b.length - bFrom
      val len = math.min(aLen, bLen)
      var i = 0
      while i < len do
        val diff = a.charAt(aFrom + i) - b.charAt(bFrom + i)
        if diff != 0 then return diff
        i += 1
      aLen - bLen

  /** Orders the non-implicit definitions before the implicit ones, then by local name. */
  private object ByImplicitThenLocalName extends java.util.Comparator[Definition]:
    def compare(x: Definition, y: Definition): Int =
      java.lang.Boolean.compare(x.modifiers.isImplicit, y.modifiers.isImplicit) match
        case 0    => ByLocalName.compare(x, y)
        case diff => diff

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
  private object NameType:
    import DefinitionType.*
    def apply(dt: DefinitionType): NameType = dt match
      case Trait | ClassDef       => TypeName
      case Module | PackageModule => TermName
  private case object TermName extends NameType
  private case object TypeName extends NameType
end NameHashing
