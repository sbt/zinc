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

package sbt
package internal
package inc

import xsbti.api._
import APIs.getAPI
import xsbt.api.{ APIUtil, SameAPI }

trait APIs {

  /**
   * The API for the class `className` at the time represented by this instance.
   * This method returns an empty API if the file had no API or is not known to this instance.
   */
  def internalAPI(className: String): AnalyzedClass

  /**
   * The API for the external class `ext` at the time represented by this instance.
   * This method returns an empty API if the file had no API or is not known to this instance.
   */
  def externalAPI(ext: String): AnalyzedClass

  def allExternals: collection.Set[String]
  def allInternalClasses: collection.Set[String]

  def ++(o: APIs): APIs

  def markInternalAPI(className: String, api: AnalyzedClass): APIs
  def markExternalAPI(binaryClassName: String, api: AnalyzedClass): APIs

  def removeInternal(removeClasses: Iterable[String]): APIs
  def filterExt(keep: String => Boolean): APIs

  def internal: Map[String, AnalyzedClass]
  def external: Map[String, AnalyzedClass]
}
object APIs {
  def apply(internal: Map[String, AnalyzedClass], external: Map[String, AnalyzedClass]): APIs =
    new MAPIs(internal, external)
  def empty: APIs = apply(Map.empty, Map.empty)

  val emptyModifiers = new Modifiers(false, false, false, false, false, false, false, false)
  val emptyName = ""
  val emptyAPI = APIUtil.emptyClassLike(emptyName, DefinitionType.ClassDef)
  val emptyAPIHash = -1
  val noCompilationStamp = -1L
  val emptyCompanions = xsbti.api.Companions.of(emptyAPI, emptyAPI)
  val emptyAnalyzedClass = xsbti.api.AnalyzedClass.of(
    noCompilationStamp,
    emptyName,
    SafeLazyProxy(emptyCompanions),
    emptyAPIHash,
    Array.empty[NameHash],
    false
  )
  def getAPI[T](map: Map[T, AnalyzedClass], className: T): AnalyzedClass =
    map.getOrElse(className, emptyAnalyzedClass)
}

private class MAPIs(
    val internal: Map[String, AnalyzedClass],
    val external: Map[String, AnalyzedClass]
) extends APIs {
  def allInternalClasses: collection.Set[String] = internal.keySet
  def allExternals: collection.Set[String] = external.keySet

  def ++(o: APIs): APIs = new MAPIs(internal ++ o.internal, external ++ o.external)

  def markInternalAPI(className: String, api: AnalyzedClass): APIs =
    new MAPIs(internal.updated(className, api), external)

  def markExternalAPI(binaryClassName: String, api: AnalyzedClass): APIs =
    new MAPIs(internal, external.updated(binaryClassName, api))

  def removeInternal(removeClasses: Iterable[String]): APIs =
    new MAPIs(internal -- removeClasses, external)
  def filterExt(keep: String => Boolean): APIs =
    new MAPIs(internal, external.filterKeys(keep).toMap)

  def internalAPI(className: String) = getAPI(internal, className)
  def externalAPI(ext: String) = getAPI(external, ext)

  override def equals(other: Any): Boolean = other match {
    case o: MAPIs => {
      def areEqual[T](x: Map[T, AnalyzedClass], y: Map[T, AnalyzedClass])(
          implicit ord: math.Ordering[T]
      ) = {
        x.size == y.size && (sorted(x) zip sorted(y) forall { z =>
          z._1._1 == z._2._1 && SameAPI(z._1._2, z._2._2)
        })
      }
      areEqual(internal, o.internal) && areEqual(external, o.external)
    }
    case _ => false
  }

  override lazy val hashCode: Int = {
    def hash[T](m: Map[T, AnalyzedClass])(implicit ord: math.Ordering[T]) =
      sorted(m).map(x => (x._1, x._2.apiHash).hashCode).hashCode
    (hash(internal), hash(external)).hashCode
  }

  override def toString: String =
    "APIs(internal: %d, subproject: %d)".format(internal.size, external.size)
  // s"MAPIs(internal = $internal, external = $external)"

  private[this] def sorted[T](
      m: Map[T, AnalyzedClass]
  )(implicit ord: math.Ordering[T]): Seq[(T, AnalyzedClass)] =
    m.toSeq.sortBy(_._1)
}
