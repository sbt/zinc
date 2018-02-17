/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
    val apiPublicDefs = publicDefs(classApi)
    val (regularDefs, implicitDefs) = apiPublicDefs.partition(deff => !deff.modifiers.isImplicit)
    val location = Location(classApi.name, NameType(classApi.definitionType))

    val forPatMat = if (optimizedSealed) {
      val classDefs = apiPublicDefs.collect {
        case classLike: ClassLike if classLike.modifiers().isSealed =>
          classLike
      }
      nameHashesForDefinitions(classDefs, location, UseScope.PatMatTarget)
    } else Array.empty[NameHash]

    nameHashesForDefinitions(regularDefs, location, UseScope.Default) ++
      nameHashesForDefinitions(implicitDefs, location, UseScope.Implicit) ++ forPatMat
  }

  private def nameHashesForDefinitions(
      defs: Iterable[Definition],
      location: Location,
      useScope: UseScope
  ): Array[NameHash] = {
    val includeSealedChildren = !optimizedSealed || useScope == UseScope.PatMatTarget
    val groupedBySimpleName = defs.groupBy(locatedDef => localName(locatedDef.name))
    val hashes =
      groupedBySimpleName.mapValues(hashLocatedDefinitions(_, location, includeSealedChildren))
    hashes.toIterable
      .map({ case (name: String, hash: Int) => NameHash.of(name, useScope, hash) })(
        collection.breakOut)
  }

  private def hashLocatedDefinitions(
      defs: Iterable[Definition],
      location: Location,
      includeSealedChildren: Boolean
  ): Int = {
    HashAPI.apply(
      _.hashDefinitionsWithExtraHash(defs, location.hashCode),
      includeDefinitions = false,
      includeSealedChildren = includeSealedChildren
    )
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

  private def publicDefs(c: ClassLike): Iterable[Definition] = {
    val visitor = new ExtractPublicDefinitions
    visitor.visitAPI(c)
    visitor.defs
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
    val m: Map[(String, UseScope), Int] = Map(nm1.map(nh => (nh.name, nh.scope) -> nh.hash): _*)
    for (nh <- nm2) {
      val key = (nh.name, nh.scope())
      if (!m.contains(key))
        m(key) = nh.hash
      else {
        val existingHash = m(key)
        // combine hashes without taking an order into account
        m(key) = Set(existingHash, nh.hash).hashCode()
      }
    }
    m.map { case ((name, scope), hash) => NameHash.of(name, scope, hash) }(collection.breakOut)
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
