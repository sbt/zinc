/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt.api

import xsbti.api.Definition
import xsbti.api.DefinitionType
import xsbti.api.ClassLike
import xsbti.api.NameHash
import xsbti.api.NameHashes

/**
 * A class that computes hashes for each group of definitions grouped by a simple name.
 *
 * See `nameHashes` method for details.
 */
class NameHashing {

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
  def nameHashes(classApi: ClassLike): NameHashes = {
    val apiPublicDefs = publicDefs(classApi)
    val (regularDefs, implicitDefs) = apiPublicDefs.partition(deff => !deff.modifiers.isImplicit)
    val location = Location(classApi.name, NameType(classApi.definitionType))
    val regularNameHashes = nameHashesForDefinitions(regularDefs, location)
    val implicitNameHashes = nameHashesForDefinitions(implicitDefs, location)
    new NameHashes(regularNameHashes.toArray, implicitNameHashes.toArray)
  }

  private def nameHashesForDefinitions(defs: Iterable[Definition], location: Location): Iterable[NameHash] = {
    val groupedBySimpleName = defs.groupBy(locatedDef => localName(locatedDef.name))
    val hashes = groupedBySimpleName.mapValues(hashLocatedDefinitions(_, location))
    hashes.toIterable.map({ case (name: String, hash: Int) => new NameHash(name, hash) })
  }

  private def hashLocatedDefinitions(defs: Iterable[Definition], location: Location): Int = {
    val defsWithExtraHashes = defs.toSeq.map(_ -> location.hashCode)
    xsbt.api.HashAPI.hashDefinitionsWithExtraHashes(defsWithExtraHashes)
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
    override def visitDefinition(d: Definition): Unit = if (d.isInstanceOf[ClassLike] || APIUtil.isNonPrivate(d)) {
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
  def merge(nm1: NameHashes, nm2: NameHashes): NameHashes = {
    val regularMembers = merge(nm1.regularMembers, nm2.regularMembers)
    val implicitMembers = merge(nm1.implicitMembers, nm2.implicitMembers)
    new NameHashes(regularMembers, implicitMembers)
  }

  private def merge(nm1: Array[NameHash], nm2: Array[NameHash]): Array[NameHash] = {
    import scala.collection.mutable.Map
    val m: Map[String, Int] = Map(nm1.map(nh => nh.name -> nh.hash): _*)
    for (nh <- nm2) {
      val name = nh.name
      if (!m.contains(name))
        m(name) = nh.hash
      else {
        val existingHash = m(name)
        // combine hashes without taking an order into account
        m(name) = Set(existingHash, nh.hash).hashCode()
      }
    }
    m.map { case (name, hash) => new NameHash(name, hash) }(collection.breakOut)
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
