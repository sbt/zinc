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

import sbt.io.{ Hash => IOHash, IO }
import xsbti.compile.analysis.{ ReadStamps, Stamp => XStamp }

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

  def sources: Map[File, XStamp]
  def binaries: Map[File, XStamp]
  def products: Map[File, XStamp]
  def markSource(src: File, s: XStamp): Stamps
  def markBinary(bin: File, className: String, s: XStamp): Stamps
  def markProduct(prod: File, s: XStamp): Stamps

  def filter(prod: File => Boolean, removeSources: Iterable[File], bin: File => Boolean): Stamps

  def ++(o: Stamps): Stamps
  def groupBy[K](prod: Map[K, File => Boolean],
                 sourcesGrouping: File => K,
                 bin: Map[K, File => Boolean]): Map[K, Stamps]
}

private[sbt] sealed abstract class StampBase extends XStamp {
  override def toString: String = this.writeStamp()
  override def hashCode(): Int = this.getValueId()
  override def equals(other: Any): Boolean = other match {
    case o: XStamp => Stamp.equivStamp.equiv(this, o)
    case _         => false
  }
}

trait WithPattern { protected def Pattern: Regex }

import java.lang.{ Long => BoxedLong }

/** Define the hash of the file contents. It's a typical stamp for compilation sources. */
final class Hash private (val hexHash: String) extends StampBase {
  // Assumes `hexHash` is a hexadecimal value.
  override def writeStamp: String = s"hash($hexHash)"
  override def getValueId: Int = hexHash.hashCode()
  override def getHash: Optional[String] = Optional.of(hexHash)
  override def getLastModified: Optional[BoxedLong] = Optional.empty[BoxedLong]
}

private[sbt] object Hash {
  private val Pattern = """hash\(((?:[0-9a-fA-F][0-9a-fA-F])+)\)""".r

  def ofFile(f: File): Hash =
    new Hash(IOHash toHex IOHash(f)) // assume toHex returns a hex string

  def fromString(s: String): Option[Hash] = {
    val m = Pattern.pattern matcher s
    if (m.matches()) Some(new Hash(m group 1))
    else None
  }

  object FromString {
    def unapply(s: String): Option[Hash] = fromString(s)
  }

  def unsafeFromString(s: String): Hash = new Hash(s)
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
  implicit val equivStamp: Equiv[XStamp] = new Equiv[XStamp] {
    def equiv(a: XStamp, b: XStamp) = (a, b) match {
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

  def fromString(s: String): XStamp = s match {
    case EmptyStamp.Value            => EmptyStamp
    case Hash.FromString(hash)       => hash
    case LastModified.Pattern(value) => new LastModified(java.lang.Long.parseLong(value))
    case _ =>
      throw new IllegalArgumentException("Unrecognized Stamp string representation: " + s)
  }

  def getStamp(map: Map[File, XStamp], src: File): XStamp = map.getOrElse(src, EmptyStamp)
}

object Stamper {
  private def tryStamp(g: => XStamp): XStamp = {
    try { g } // TODO: Double check correctness. Why should we not report an exception here?
    catch { case _: IOException => EmptyStamp }
  }

  val forHash: File => XStamp = (toStamp: File) => tryStamp(Hash.ofFile(toStamp))
  val forLastModified: File => XStamp = (toStamp: File) =>
    tryStamp(new LastModified(IO.getModifiedTimeOrZero(toStamp)))
  def forLastModifiedInJar(jar: File): File => XStamp = {
    val stamps = JarUtils.readStamps(jar)
    (file: File) =>
      new LastModified(stamps(file))
  }
}

object Stamps {

  /**
   * Creates a ReadStamps instance that will calculate and cache the stamp for sources and binaries
   * on the first request according to the provided `srcStamp` and `binStamp` functions.  Each
   * stamp is calculated separately on demand.
   * The stamp for a product is always recalculated.
   */
  def initial(prodStamp: File => XStamp,
              srcStamp: File => XStamp,
              binStamp: File => XStamp): ReadStamps =
    new InitialStamps(prodStamp, srcStamp, binStamp)

  def empty: Stamps = {
    // Use a TreeMap to avoid sorting when serializing
    val eSt = TreeMap.empty[File, XStamp]
    apply(eSt, eSt, eSt)
  }
  def apply(products: Map[File, XStamp],
            sources: Map[File, XStamp],
            binaries: Map[File, XStamp]): Stamps =
    new MStamps(products, sources, binaries)

  def merge(stamps: Traversable[Stamps]): Stamps = (Stamps.empty /: stamps)(_ ++ _)
}

private class MStamps(val products: Map[File, XStamp],
                      val sources: Map[File, XStamp],
                      val binaries: Map[File, XStamp])
    extends Stamps {

  import scala.collection.JavaConverters.mapAsJavaMapConverter
  override def getAllBinaryStamps: util.Map[File, XStamp] =
    mapAsJavaMapConverter(binaries).asJava
  override def getAllProductStamps: util.Map[File, XStamp] =
    mapAsJavaMapConverter(products).asJava
  override def getAllSourceStamps: util.Map[File, XStamp] =
    mapAsJavaMapConverter(sources).asJava

  def allSources: collection.Set[File] = sources.keySet
  def allBinaries: collection.Set[File] = binaries.keySet
  def allProducts: collection.Set[File] = products.keySet

  def ++(o: Stamps): Stamps =
    new MStamps(products ++ o.products, sources ++ o.sources, binaries ++ o.binaries)

  def markSource(src: File, s: XStamp): Stamps =
    new MStamps(products, sources.updated(src, s), binaries)

  def markBinary(bin: File, className: String, s: XStamp): Stamps =
    new MStamps(products, sources, binaries.updated(bin, s))

  def markProduct(prod: File, s: XStamp): Stamps =
    new MStamps(products.updated(prod, s), sources, binaries)

  def filter(prod: File => Boolean, removeSources: Iterable[File], bin: File => Boolean): Stamps =
    new MStamps(products.filterKeys(prod), sources -- removeSources, binaries.filterKeys(bin))

  def groupBy[K](prod: Map[K, File => Boolean],
                 f: File => K,
                 bin: Map[K, File => Boolean]): Map[K, Stamps] = {
    val sourcesMap: Map[K, Map[File, XStamp]] = sources.groupBy(x => f(x._1))

    val constFalse = (f: File) => false
    def kStamps(k: K): Stamps = new MStamps(
      products.filterKeys(prod.getOrElse(k, constFalse)),
      sourcesMap.getOrElse(k, Map.empty[File, XStamp]),
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

private class InitialStamps(prodStamp: File => XStamp,
                            srcStamp: File => XStamp,
                            binStamp: File => XStamp)
    extends ReadStamps {
  import collection.mutable.{ HashMap, Map }

  // cached stamps for files that do not change during compilation
  private val sources: Map[File, XStamp] = new HashMap
  private val binaries: Map[File, XStamp] = new HashMap

  import scala.collection.JavaConverters.mapAsJavaMapConverter
  override def getAllBinaryStamps: util.Map[File, XStamp] =
    mapAsJavaMapConverter(binaries).asJava
  override def getAllSourceStamps: util.Map[File, XStamp] =
    mapAsJavaMapConverter(sources).asJava
  override def getAllProductStamps: util.Map[File, XStamp] = new util.HashMap()

  override def product(prod: File): XStamp = prodStamp(prod)
  override def source(src: File): XStamp =
    synchronized { sources.getOrElseUpdate(src, srcStamp(src)) }
  override def binary(bin: File): XStamp =
    synchronized { binaries.getOrElseUpdate(bin, binStamp(bin)) }
}
