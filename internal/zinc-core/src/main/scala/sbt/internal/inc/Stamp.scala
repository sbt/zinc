/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.{ File, IOException }
import java.util
import java.util.Optional

import sbt.io.{ Hash => IOHash }
import xsbti.compile.analysis.{ ReadStamps, Stamp }

import scala.collection.immutable.TreeMap
import scala.util.matching.Regex

/**
 * Provides a richer interface to read and write stamps associated with files.
 *
 * This interface is meant for internal use and is Scala idiomatic. It implements the
 * Java interface [[ReadStamps]] that is exposed in the [[xsbti.compile.CompileAnalysis]].
 */
trait Stamps extends ReadStamps {
  def allSources: collection.Set[File]
  def allBinaries: collection.Set[File]
  def allProducts: collection.Set[File]

  def sources: Map[File, Stamp]
  def binaries: Map[File, Stamp]
  def products: Map[File, Stamp]
  def markSource(src: File, s: Stamp): Stamps
  def markBinary(bin: File, className: String, s: Stamp): Stamps
  def markProduct(prod: File, s: Stamp): Stamps

  def filter(prod: File => Boolean, removeSources: Iterable[File], bin: File => Boolean): Stamps

  def ++(o: Stamps): Stamps
  def groupBy[K](prod: Map[K, File => Boolean],
                 sourcesGrouping: File => K,
                 bin: Map[K, File => Boolean]): Map[K, Stamps]
}

private[sbt] sealed abstract class StampBase extends Stamp {
  override def toString: String = this.writeStamp()
  override def hashCode(): Int = this.getValueId()
  override def equals(other: Any): Boolean = other match {
    case o: Stamp => Stamp.equivStamp.equiv(this, o)
    case _        => false
  }
}

trait WithPattern { protected def Pattern: Regex }

import java.lang.{ Long => BoxedLong }

/** Define the hash of the file contents. It's a typical stamp for compilation sources. */
final class Hash(val hexHash: String) extends StampBase {
  override def writeStamp: String = s"hash($hexHash)"
  override def getValueId: Int = hexHash.hashCode()
  override def getHash: Optional[String] = Optional.of(hexHash)
  override def getLastModified: Optional[BoxedLong] = Optional.empty[BoxedLong]
}

private[sbt] object Hash extends WithPattern {
  final val Pattern = """hash\((\w+)\)""".r
}

/** Define the last modified time of the file. It's a typical stamp for class files and products. */
final class LastModified(val value: Long) extends StampBase {
  override def writeStamp: String = s"lastModified(${value})"
  override def getValueId: Int = (value ^ (value >>> 32)).toInt
  override def getHash: Optional[String] = Optional.empty[String]
  override def getLastModified: Optional[BoxedLong] = Optional.of(value)
}

/** Defines an empty stamp. */
private[sbt] object EmptyStamp extends StampBase {
  // Use `absent` because of historic reasons -- replacement of old `Exists` representation
  final val Value = "absent"
  override def writeStamp: String = Value
  override def getValueId: Int = System.identityHashCode(this)
  override def getHash: Optional[String] = Optional.empty[String]
  override def getLastModified: Optional[BoxedLong] = Optional.empty[BoxedLong]
}

private[inc] object LastModified extends WithPattern {
  final val Pattern = """lastModified\((\d+)\)""".r
}

object Stamp {
  private final val maxModificationDifferenceInMillis = 100L
  implicit val equivStamp: Equiv[Stamp] = new Equiv[Stamp] {
    def equiv(a: Stamp, b: Stamp) = (a, b) match {
      case (h1: Hash, h2: Hash) => h1.hexHash == h2.hexHash
      // Windows is handling this differently sometimes...
      case (lm1: LastModified, lm2: LastModified) =>
        lm1.value == lm2.value ||
          Math.abs(lm1.value - lm2.value) < maxModificationDifferenceInMillis
      case (stampA, stampB) =>
        // This part of code should not depend on `equals`
        // Checking for (EmptyStamp, EmptyStamp) produces SOE
        stampA.eq(EmptyStamp) && stampB.eq(EmptyStamp)
    }
  }

  def fromString(s: String): Stamp = s match {
    case EmptyStamp.Value            => EmptyStamp
    case Hash.Pattern(value)         => new Hash(value)
    case LastModified.Pattern(value) => new LastModified(java.lang.Long.parseLong(value))
    case _ =>
      throw new IllegalArgumentException("Unrecognized Stamp string representation: " + s)
  }

  def getStamp(map: Map[File, Stamp], src: File): Stamp = map.getOrElse(src, EmptyStamp)
}

object Stamper {
  private def tryStamp(g: => Stamp): Stamp = {
    try { g } // TODO: Double check correctness. Why should we not report an exception here?
    catch { case i: IOException => EmptyStamp }
  }

  val forHash = (toStamp: File) => tryStamp(new Hash(IOHash.toHex(IOHash(toStamp))))
  val forLastModified = (toStamp: File) => tryStamp(new LastModified(toStamp.lastModified()))
}

object Stamps {

  /**
   * Creates a ReadStamps instance that will calculate and cache the stamp for sources and binaries
   * on the first request according to the provided `srcStamp` and `binStamp` functions.  Each
   * stamp is calculated separately on demand.
   * The stamp for a product is always recalculated.
   */
  def initial(prodStamp: File => Stamp,
              srcStamp: File => Stamp,
              binStamp: File => Stamp): ReadStamps =
    new InitialStamps(prodStamp, srcStamp, binStamp)

  def empty: Stamps = {
    // Use a TreeMap to avoid sorting when serializing
    val eSt = TreeMap.empty[File, Stamp]
    apply(eSt, eSt, eSt)
  }
  def apply(products: Map[File, Stamp],
            sources: Map[File, Stamp],
            binaries: Map[File, Stamp]): Stamps =
    new MStamps(products, sources, binaries)

  def merge(stamps: Traversable[Stamps]): Stamps = (Stamps.empty /: stamps)(_ ++ _)
}

private class MStamps(val products: Map[File, Stamp],
                      val sources: Map[File, Stamp],
                      val binaries: Map[File, Stamp])
    extends Stamps {

  import scala.collection.JavaConverters.mapAsJavaMapConverter
  override def getAllBinaryStamps: util.Map[File, Stamp] =
    mapAsJavaMapConverter(binaries).asJava
  override def getAllProductStamps: util.Map[File, Stamp] =
    mapAsJavaMapConverter(products).asJava
  override def getAllSourceStamps: util.Map[File, Stamp] =
    mapAsJavaMapConverter(sources).asJava

  def allSources: collection.Set[File] = sources.keySet
  def allBinaries: collection.Set[File] = binaries.keySet
  def allProducts: collection.Set[File] = products.keySet

  def ++(o: Stamps): Stamps =
    new MStamps(products ++ o.products, sources ++ o.sources, binaries ++ o.binaries)

  def markSource(src: File, s: Stamp): Stamps =
    new MStamps(products, sources.updated(src, s), binaries)

  def markBinary(bin: File, className: String, s: Stamp): Stamps =
    new MStamps(products, sources, binaries.updated(bin, s))

  def markProduct(prod: File, s: Stamp): Stamps =
    new MStamps(products.updated(prod, s), sources, binaries)

  def filter(prod: File => Boolean, removeSources: Iterable[File], bin: File => Boolean): Stamps =
    new MStamps(products.filterKeys(prod), sources -- removeSources, binaries.filterKeys(bin))

  def groupBy[K](prod: Map[K, File => Boolean],
                 f: File => K,
                 bin: Map[K, File => Boolean]): Map[K, Stamps] = {
    val sourcesMap: Map[K, Map[File, Stamp]] = sources.groupBy(x => f(x._1))

    val constFalse = (f: File) => false
    def kStamps(k: K): Stamps = new MStamps(
      products.filterKeys(prod.getOrElse(k, constFalse)),
      sourcesMap.getOrElse(k, Map.empty[File, Stamp]),
      binaries.filterKeys(bin.getOrElse(k, constFalse))
    )

    (for (k <- prod.keySet ++ sourcesMap.keySet ++ bin.keySet) yield (k, kStamps(k))).toMap
  }

  override def product(prod: File) = Stamp.getStamp(products, prod)
  override def source(src: File) = Stamp.getStamp(sources, src)
  override def binary(bin: File) = Stamp.getStamp(binaries, bin)

  override def equals(other: Any): Boolean = other match {
    case o: MStamps => products == o.products && sources == o.sources && binaries == o.binaries
    case _          => false
  }

  override lazy val hashCode: Int = (products :: sources :: binaries :: Nil).hashCode

  override def toString: String =
    "Stamps for: %d products, %d sources, %d binaries".format(products.size,
                                                              sources.size,
                                                              binaries.size)
}

private class InitialStamps(prodStamp: File => Stamp,
                            srcStamp: File => Stamp,
                            binStamp: File => Stamp)
    extends ReadStamps {
  import collection.mutable.{ HashMap, Map }
  // cached stamps for files that do not change during compilation
  private val sources: Map[File, Stamp] = new HashMap
  private val binaries: Map[File, Stamp] = new HashMap

  import scala.collection.JavaConverters.mapAsJavaMapConverter
  override def getAllBinaryStamps: util.Map[File, Stamp] =
    mapAsJavaMapConverter(binaries).asJava
  override def getAllSourceStamps: util.Map[File, Stamp] =
    mapAsJavaMapConverter(sources).asJava
  override def getAllProductStamps: util.Map[File, Stamp] = new util.HashMap()

  override def product(prod: File): Stamp = prodStamp(prod)
  override def source(src: File): Stamp =
    synchronized { sources.getOrElseUpdate(src, srcStamp(src)) }
  override def binary(bin: File): Stamp =
    synchronized { binaries.getOrElseUpdate(bin, binStamp(bin)) }
}
