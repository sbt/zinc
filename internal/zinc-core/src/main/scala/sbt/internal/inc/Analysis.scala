/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import sbt.internal.inc.Analysis.{ LocalProduct, NonLocalProduct }
import java.io.File

import xsbti.api.{ AnalyzedClass, InternalDependency, ExternalDependency }
import xsbti.compile.CompileAnalysis

trait Analysis extends CompileAnalysis {
  val stamps: Stamps
  val apis: APIs
  /** Mappings between sources, classes, and binaries. */
  val relations: Relations
  val infos: SourceInfos
  /**
   * Information about compiler runs accumulated since `clean` command has been run.
   *
   * The main use-case for using `compilations` field is to determine how
   * many iterations it took to compilen give code. The `Compilation` object
   * are also stored in `Source` objects so there's an indirect way to recover
   * information about files being recompiled in every iteration.
   *
   * The incremental compilation algorithm doesn't use information stored in
   * `compilations`. It's safe to prune contents of that field without breaking
   * internal consistency of the entire Analysis object.
   */
  val compilations: Compilations

  /** Concatenates Analysis objects naively, i.e., doesn't internalize external deps on added files. See `Analysis.merge`. */
  def ++(other: Analysis): Analysis

  /** Drops all analysis information for `sources` naively, i.e., doesn't externalize internal deps on removed files. */
  def --(sources: Iterable[File]): Analysis

  def copy(stamps: Stamps = stamps, apis: APIs = apis, relations: Relations = relations, infos: SourceInfos = infos,
    compilations: Compilations = compilations): Analysis

  def addSource(src: File, apis: Iterable[AnalyzedClass], stamp: Stamp, info: SourceInfo,
    nonLocalProducts: Iterable[NonLocalProduct],
    localProducts: Iterable[LocalProduct],
    internalDeps: Iterable[InternalDependency],
    externalDeps: Iterable[ExternalDependency],
    binaryDeps: Iterable[(File, String, Stamp)]): Analysis

  override lazy val toString = Analysis.summary(this)
}

object Analysis {
  case class NonLocalProduct(className: String, binaryClassName: String, classFile: File, classFileStamp: Stamp)
  case class LocalProduct(classFile: File, classFileStamp: Stamp)
  lazy val Empty: Analysis = new MAnalysis(Stamps.empty, APIs.empty, Relations.empty, SourceInfos.empty, Compilations.empty)
  def empty: Analysis = new MAnalysis(Stamps.empty, APIs.empty,
    Relations.empty, SourceInfos.empty, Compilations.empty)

  def summary(a: Analysis): String =
    {
      def sourceFileForClass(className: String): File =
        a.relations.definesClass(className).headOption.getOrElse {
          sys.error(s"Can't find source file for $className")
        }
      def isJavaClass(className: String): Boolean =
        sourceFileForClass(className).getName.endsWith(".java")
      val (j, s) = a.apis.allInternalClasses.partition(isJavaClass)
      val c = a.stamps.allProducts
      val ext = a.apis.allExternals
      val jars = a.relations.allLibraryDeps.filter(_.getName.endsWith(".jar"))
      val unreportedCount = a.infos.allInfos.values.map(_.unreportedProblems.size).sum
      val sections =
        counted("Scala source", "", "s", s.size) ++
          counted("Java source", "", "s", j.size) ++
          counted("class", "", "es", c.size) ++
          counted("external source dependenc", "y", "ies", ext.size) ++
          counted("binary dependenc", "y", "ies", jars.size) ++
          counted("unreported warning", "", "s", unreportedCount)
      sections.mkString("Analysis: ", ", ", "")
    }

  def counted(prefix: String, single: String, plural: String, count: Int): Option[String] =
    count match {
      case 0 => None
      case 1 => Some("1 " + prefix + single)
      case x => Some(x.toString + " " + prefix + plural)
    }

}
private class MAnalysis(val stamps: Stamps, val apis: APIs, val relations: Relations, val infos: SourceInfos, val compilations: Compilations) extends Analysis {
  def ++(o: Analysis): Analysis = new MAnalysis(stamps ++ o.stamps, apis ++ o.apis, relations ++ o.relations,
    infos ++ o.infos, compilations ++ o.compilations)

  def --(sources: Iterable[File]): Analysis =
    {
      val newRelations = relations -- sources
      def keep[T](f: (Relations, T) => Set[_]): T => Boolean = f(newRelations, _).nonEmpty

      val classesInSrcs = sources.flatMap(relations.classNames)
      val newAPIs = apis.removeInternal(classesInSrcs).filterExt(keep(_ usesExternal _))
      val newStamps = stamps.filter(keep(_ produced _), sources, keep(_ usesLibrary _))
      val newInfos = infos -- sources
      new MAnalysis(newStamps, newAPIs, newRelations, newInfos, compilations)
    }

  def copy(stamps: Stamps, apis: APIs, relations: Relations, infos: SourceInfos, compilations: Compilations = compilations): Analysis =
    new MAnalysis(stamps, apis, relations, infos, compilations)

  def addSource(src: File, apis: Iterable[AnalyzedClass], stamp: Stamp, info: SourceInfo,
    nonLocalProducts: Iterable[NonLocalProduct],
    localProducts: Iterable[LocalProduct],
    internalDeps: Iterable[InternalDependency],
    externalDeps: Iterable[ExternalDependency],
    binaryDeps: Iterable[(File, String, Stamp)]): Analysis = {

    val newStamps = {
      val nonLocalProductStamps = nonLocalProducts.foldLeft(stamps.markInternalSource(src, stamp)) {
        case (tmpStamps, nonLocalProduct) =>
          tmpStamps.markProduct(nonLocalProduct.classFile, nonLocalProduct.classFileStamp)
      }

      val allProductStamps = localProducts.foldLeft(nonLocalProductStamps) {
        case (tmpStamps, localProduct) =>
          tmpStamps.markProduct(localProduct.classFile, localProduct.classFileStamp)
      }

      binaryDeps.foldLeft(allProductStamps) {
        case (tmpStamps, (toBinary, className, binStamp)) => tmpStamps.markBinary(toBinary, className, binStamp)
      }
    }

    val newInternalAPIs = apis.foldLeft(this.apis) {
      case (tmpApis, analyzedClass) => tmpApis.markInternalAPI(analyzedClass.name, analyzedClass)
    }

    val newAPIs = externalDeps.foldLeft(newInternalAPIs) {
      case (tmpApis, ed: ExternalDependency) => tmpApis.markExternalAPI(ed.targetProductClassName, ed.targetClass)
    }

    val allProducts = nonLocalProducts.map(_.classFile) ++ localProducts.map(_.classFile)
    val classes = nonLocalProducts.map(p => p.className -> p.binaryClassName)

    val newRelations = relations.addSource(src, allProducts, classes, internalDeps, externalDeps, binaryDeps)

    copy(newStamps, newAPIs, newRelations, infos.add(src, info))
  }

  override def equals(other: Any) = other match {
    // Note: Equality doesn't consider source infos or compilations.
    case o: MAnalysis => stamps == o.stamps && apis == o.apis && relations == o.relations
    case _            => false
  }

  override lazy val hashCode = (stamps :: apis :: relations :: Nil).hashCode
}
