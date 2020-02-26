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

import java.io.File
import java.io.{ File, IOException }
import java.util
import java.util.Optional
import java.nio.file.Path

import sbt.io.{ Hash => IOHash, IO }
import xsbti.{ FileConverter, VirtualFile, VirtualFileRef }
import xsbti.compile.analysis.{ ReadStamps, Stamp => XStamp }

import scala.collection.immutable.TreeMap
import scala.util.matching.Regex

/**
 * Provides a richer interface to read and write stamps associated with files.
 *
 * This interface is meant for internal use and is Scala idiomatic. It implements the
 * Java interface `ReadStamps` that is exposed in the `xsbti.compile.CompileAnalysis`.
 */
trait Stamps extends ReadStamps {
  def allSources: collection.Set[VirtualFileRef]
  def allLibraries: collection.Set[VirtualFileRef]
  def allProducts: collection.Set[VirtualFileRef]

  def sources: Map[VirtualFileRef, XStamp]
  def libraries: Map[VirtualFileRef, XStamp]
  def products: Map[VirtualFileRef, XStamp]
  def markSource(src: VirtualFileRef, s: XStamp): Stamps
  def markLibrary(bin: VirtualFileRef, className: String, s: XStamp): Stamps
  def markProduct(prod: VirtualFileRef, s: XStamp): Stamps

  def filter(
      prod: VirtualFileRef => Boolean,
      removeSources: Iterable[VirtualFileRef],
      lib: VirtualFileRef => Boolean
  ): Stamps

  def ++(o: Stamps): Stamps
  def groupBy[K](
      prod: Map[K, VirtualFileRef => Boolean],
      sourcesGrouping: VirtualFileRef => K,
      lib: Map[K, VirtualFileRef => Boolean]
  ): Map[K, Stamps]
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
final class FarmHash private (val hashValue: Long) extends StampBase {
  override def writeStamp: String = s"farm(${BoxedLong.toHexString(hashValue)})"
  override def getValueId: Int = hashValue.##
  override def getHash: Optional[String] = Optional.of(BoxedLong.toHexString(hashValue))
  override def getLastModified: Optional[BoxedLong] = Optional.empty[BoxedLong]
}

object FarmHash {
  private val Pattern = """farm\(((?:[0-9a-fA-F])+)\)""".r
  def fromLong(hashValue: Long): FarmHash = new FarmHash(hashValue)

  def ofFile(f: VirtualFile): FarmHash =
    fromLong(f.contentHash)

  def ofPath(path: Path): FarmHash =
    fromLong(HashUtil.farmHash(path))

  def fromString(s: String): Option[FarmHash] = {
    val m = Pattern.pattern matcher s
    if (m.matches()) Some(FarmHash.fromLong(BoxedLong.parseUnsignedLong(m.group(1), 16)))
    else None
  }

  object FromString {
    def unapply(s: String): Option[FarmHash] = fromString(s)
  }
}

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
      case (h1: FarmHash, h2: FarmHash) => h1.hashValue == h2.hashValue
      case (h1: Hash, h2: Hash)         => h1.hexHash == h2.hexHash
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
    case FarmHash.FromString(hash)   => hash
    case Hash.FromString(hash)       => hash
    case LastModified.Pattern(value) => new LastModified(java.lang.Long.parseLong(value))
    case _ =>
      throw new IllegalArgumentException("Unrecognized Stamp string representation: " + s)
  }

  def getStamp(map: Map[File, XStamp], src: File): XStamp = map.getOrElse(src, EmptyStamp)
  def getVStamp(map: Map[VirtualFileRef, XStamp], src: VirtualFile): XStamp =
    map.getOrElse(src, EmptyStamp)
  def getVOStamp(map: Map[VirtualFileRef, XStamp], src: VirtualFileRef): XStamp =
    map.getOrElse(src, EmptyStamp)
}

object Stamper {
  private def tryStamp(g: => XStamp): XStamp = {
    try {
      g
    } // TODO: Double check correctness. Why should we not report an exception here?
    catch { case _: IOException => EmptyStamp }
  }

  val forContentHash: VirtualFile => XStamp = (toStamp: VirtualFile) =>
    tryStamp(FarmHash.ofFile(toStamp))

  val forFarmHashP: Path => XStamp = (toStamp: Path) => tryStamp(FarmHash.ofPath(toStamp))

  val forLastModifiedP: Path => XStamp = (toStamp: Path) =>
    tryStamp(new LastModified(IO.getModifiedTimeOrZero(toStamp.toFile)))

  def forLastModifiedInRootPaths(converter: FileConverter): VirtualFileRef => XStamp = {
    (toStamp: VirtualFileRef) =>
      {
        val p = converter.toPath(toStamp)
        JarUtils.getJarInClassInJar(p) match {
          case Some(outputJar) =>
            tryStamp {
              val stamps = JarUtils.readStamps(outputJar)
              val result = new LastModified(stamps(p))
              result
            }
          case _ =>
            tryStamp {
              val result = new LastModified(IO.getModifiedTimeOrZero(p.toFile))
              result
            }
        }
      }
  }

  def forHashInRootPaths(converter: FileConverter): VirtualFileRef => XStamp = {
    (toStamp: VirtualFileRef) =>
      {
        toStamp.name match {
          case "rt.jar" => EmptyStamp
          case _ =>
            val p = converter.toPath(toStamp)
            JarUtils.getJarInClassInJar(p) match {
              case Some(outputJar) =>
                tryStamp {
                  FarmHash.ofPath(outputJar)
                }
              case _ =>
                tryStamp {
                  FarmHash.ofPath(p)
                }
            }
        }
      }
  }

  private[sbt] def timeWrap(
      cache: collection.mutable.Map[VirtualFileRef, (Long, XStamp)],
      converter: FileConverter,
      getStamp: VirtualFileRef => XStamp
  ): VirtualFileRef => XStamp = { key: VirtualFileRef =>
    val p = converter.toPath(key)
    val ts = try {
      IO.getModifiedTimeOrZero(p.toFile)
    } catch {
      case _: Throwable => 0L
    }
    synchronized {
      cache.get(key) match {
        case Some((ts1, value)) if ts == ts1 && ts > 0 => value
        case _ =>
          val value = getStamp(key)
          cache.put(key, (ts, value))
          value
      }
    }
  }
}

object Stamps {

  /**
   * Creates a ReadStamps instance that will calculate and cache the stamp for sources and binaries
   * on the first request according to the provided `srcStamp` and `binStamp` functions.  Each
   * stamp is calculated separately on demand.
   * The stamp for a product is always recalculated.
   */
  def initial(
      prodStamp: VirtualFileRef => XStamp,
      srcStamp: VirtualFile => XStamp,
      libStamp: VirtualFileRef => XStamp
  ): ReadStamps =
    new InitialStamps(uncachedStamps(prodStamp, srcStamp, libStamp))

  def initial(underlying: ReadStamps): ReadStamps = new InitialStamps(underlying)

  def timeWrapLibraryStamps(underlying: ReadStamps, converter: FileConverter): ReadStamps =
    new TimeWrapLibraryStamps(underlying, converter)

  def timeWrapLibraryStamps(converter: FileConverter): ReadStamps =
    new TimeWrapLibraryStamps(uncachedStamps(converter), converter)

  def uncachedStamps(converter: FileConverter): ReadStamps =
    uncachedStamps(
      Stamper.forHashInRootPaths(converter),
      Stamper.forContentHash,
      Stamper.forHashInRootPaths(converter)
    )

  def uncachedStamps(
      prodStamp: VirtualFileRef => XStamp,
      srcStamp: VirtualFile => XStamp,
      libStamp: VirtualFileRef => XStamp
  ): ReadStamps =
    new UncachedStamps(prodStamp, srcStamp, libStamp)

  def empty: Stamps = {
    // Use a TreeMap to avoid sorting when serializing
    import VirtualFileUtil._
    val eSt = TreeMap.empty[VirtualFileRef, XStamp]
    apply(eSt, eSt, eSt)
  }
  def apply(
      products: Map[VirtualFileRef, XStamp],
      sources: Map[VirtualFileRef, XStamp],
      libraries: Map[VirtualFileRef, XStamp]
  ): Stamps =
    new MStamps(products, sources, libraries)

  def merge(stamps: Traversable[Stamps]): Stamps = stamps.foldLeft(Stamps.empty)(_ ++ _)
}

private class MStamps(
    val products: Map[VirtualFileRef, XStamp],
    val sources: Map[VirtualFileRef, XStamp],
    val libraries: Map[VirtualFileRef, XStamp]
) extends Stamps {

  import scala.collection.JavaConverters.mapAsJavaMapConverter
  override def getAllLibraryStamps: util.Map[VirtualFileRef, XStamp] =
    mapAsJavaMapConverter(libraries).asJava
  override def getAllProductStamps: util.Map[VirtualFileRef, XStamp] =
    mapAsJavaMapConverter(products).asJava
  override def getAllSourceStamps: util.Map[VirtualFileRef, XStamp] =
    mapAsJavaMapConverter(sources).asJava

  def allSources: collection.Set[VirtualFileRef] = sources.keySet
  def allLibraries: collection.Set[VirtualFileRef] = libraries.keySet
  def allProducts: collection.Set[VirtualFileRef] = products.keySet

  def ++(o: Stamps): Stamps =
    new MStamps(products ++ o.products, sources ++ o.sources, libraries ++ o.libraries)

  def markSource(src: VirtualFileRef, s: XStamp): Stamps = {
    // sys.error(s"markSource($src, $s)")
    new MStamps(products, sources.updated(src, s), libraries)
  }

  def markLibrary(lib: VirtualFileRef, className: String, s: XStamp): Stamps =
    new MStamps(products, sources, libraries.updated(lib, s))

  def markProduct(prod: VirtualFileRef, s: XStamp): Stamps =
    new MStamps(products.updated(prod, s), sources, libraries)

  def filter(
      prod: VirtualFileRef => Boolean,
      removeSources: Iterable[VirtualFileRef],
      lib: VirtualFileRef => Boolean
  ): Stamps =
    new MStamps(products.filterKeys(prod), {
      val rs = removeSources.toSet
      Map(sources.toSeq.filter {
        case (file, stamp) => !rs(file)
      }: _*)
    }, libraries.filterKeys(lib))

  def groupBy[K](
      prod: Map[K, VirtualFileRef => Boolean],
      f: VirtualFileRef => K,
      lib: Map[K, VirtualFileRef => Boolean]
  ): Map[K, Stamps] = {
    val sourcesMap: Map[K, Map[VirtualFileRef, XStamp]] = sources.groupBy(x => f(x._1))

    val constFalse = (f: VirtualFileRef) => false
    def kStamps(k: K): Stamps = new MStamps(
      products.filterKeys(prod.getOrElse(k, constFalse)),
      sourcesMap.getOrElse(k, Map.empty[VirtualFileRef, XStamp]),
      libraries.filterKeys(lib.getOrElse(k, constFalse))
    )

    (for (k <- prod.keySet ++ sourcesMap.keySet ++ lib.keySet) yield (k, kStamps(k))).toMap
  }

  override def product(prod: VirtualFileRef) = Stamp.getVOStamp(products, prod)
  override def source(src: VirtualFile) = Stamp.getVStamp(sources, src)
  override def library(lib: VirtualFileRef) = Stamp.getVOStamp(libraries, lib)

  override def equals(other: Any): Boolean = other match {
    case o: MStamps => products == o.products && sources == o.sources && libraries == o.libraries
    case _          => false
  }

  override lazy val hashCode: Int = (products :: sources :: libraries :: Nil).hashCode

  override def toString: String =
    "Stamps for: %d products, %d sources, %d libraries".format(
      products.size,
      sources.size,
      libraries.size
    ) + " " + sources.toString
}

/**
 * Stamp cache used for a single compilation.
 *
 * @param underlying
 */
private class InitialStamps(
    underlying: ReadStamps
) extends ReadStamps {
  import collection.mutable.{ HashMap, Map }

  // cached stamps for files that do not change during compilation
  private val sources: Map[VirtualFileRef, XStamp] = new HashMap
  private val libraries: Map[VirtualFileRef, XStamp] = new HashMap

  import scala.collection.JavaConverters.mapAsJavaMapConverter
  override def getAllLibraryStamps: util.Map[VirtualFileRef, XStamp] =
    mapAsJavaMapConverter(libraries).asJava
  override def getAllSourceStamps: util.Map[VirtualFileRef, XStamp] =
    mapAsJavaMapConverter(sources).asJava
  override def getAllProductStamps: util.Map[VirtualFileRef, XStamp] =
    new util.HashMap()

  override def product(prod: VirtualFileRef): XStamp = underlying.product(prod)
  override def source(src: VirtualFile): XStamp =
    synchronized { sources.getOrElseUpdate(src, underlying.source(src)) }
  override def library(lib: VirtualFileRef): XStamp =
    synchronized { libraries.getOrElseUpdate(lib, underlying.library(lib)) }
}

private class TimeWrapLibraryStamps(
    underlying: ReadStamps,
    converter: FileConverter
) extends ReadStamps {
  import collection.mutable.{ HashMap, Map }

  // cached stamps for files that do not change during compilation
  private val libraries: Map[VirtualFileRef, (Long, XStamp)] = new HashMap

  import scala.collection.JavaConverters.mapAsJavaMapConverter
  override def getAllLibraryStamps: util.Map[VirtualFileRef, XStamp] =
    mapAsJavaMapConverter(libraries map { case (k, (_, v2)) => (k, v2) }).asJava
  override def getAllSourceStamps: util.Map[VirtualFileRef, XStamp] =
    underlying.getAllSourceStamps
  override def getAllProductStamps: util.Map[VirtualFileRef, XStamp] =
    underlying.getAllProductStamps

  override def product(prod: VirtualFileRef): XStamp = underlying.product(prod)
  override def source(src: VirtualFile): XStamp = underlying.source(src)
  val library0 = Stamper.timeWrap(libraries, converter, underlying.library(_))
  override def library(lib: VirtualFileRef): XStamp = library0(lib)
}

/**
 * Creates a raw stamper without caching.
 */
private class UncachedStamps(
    prodStamp: VirtualFileRef => XStamp,
    srcStamp: VirtualFile => XStamp,
    libStamp: VirtualFileRef => XStamp
) extends ReadStamps {
  import VirtualFileUtil._
  import scala.collection.JavaConverters.mapAsJavaMapConverter
  val eSt = mapAsJavaMapConverter(TreeMap.empty[VirtualFileRef, XStamp]).asJava

  override def getAllLibraryStamps: util.Map[VirtualFileRef, XStamp] = eSt
  override def getAllSourceStamps: util.Map[VirtualFileRef, XStamp] = eSt
  override def getAllProductStamps: util.Map[VirtualFileRef, XStamp] = eSt

  override def product(prod: VirtualFileRef): XStamp = prodStamp(prod)
  override def source(src: VirtualFile): XStamp = srcStamp(src)
  override def library(lib: VirtualFileRef): XStamp = libStamp(lib)
}
