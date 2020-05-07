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

import sbt.internal.inc.Analysis.{ LocalProduct, NonLocalProduct }
import java.nio.file.{ Path, Paths }

import xsbti.VirtualFileRef
import xsbti.api.{ AnalyzedClass, ExternalDependency, InternalDependency }
import xsbti.compile.{ CompileAnalysis, SingleOutput, Output }
import xsbti.compile.analysis.{
  ReadCompilations,
  ReadSourceInfos,
  ReadStamps,
  SourceInfo,
  Stamp => XStamp
}

trait Analysis extends CompileAnalysis {
  val stamps: Stamps
  val apis: APIs

  /** Mappings between sources, classes, and binaries. */
  val relations: Relations
  val infos: SourceInfos

  override def readStamps: ReadStamps = stamps
  override def readSourceInfos: ReadSourceInfos = infos
  override def readCompilations: ReadCompilations = compilations

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
  def --(sources: Iterable[VirtualFileRef]): Analysis

  def copy(
      stamps: Stamps = stamps,
      apis: APIs = apis,
      relations: Relations = relations,
      infos: SourceInfos = infos,
      compilations: Compilations = compilations
  ): Analysis

  def addSource(
      src: VirtualFileRef,
      apis: Iterable[AnalyzedClass],
      stamp: XStamp,
      info: SourceInfo,
      nonLocalProducts: Iterable[NonLocalProduct],
      localProducts: Iterable[LocalProduct],
      internalDeps: Iterable[InternalDependency],
      externalDeps: Iterable[ExternalDependency],
      libraryDeps: Iterable[(VirtualFileRef, String, XStamp)]
  ): Analysis

  override lazy val toString = Analysis.summary(this)
}

object Analysis {
  case class NonLocalProduct(
      className: String,
      binaryClassName: String,
      classFile: VirtualFileRef,
      classFileStamp: XStamp
  )

  case class LocalProduct(classFile: VirtualFileRef, classFileStamp: XStamp)

  lazy val Empty: Analysis =
    new MAnalysis(Stamps.empty, APIs.empty, Relations.empty, SourceInfos.empty, Compilations.empty)

  def empty: Analysis = Empty

  def summary(a: Analysis): String = {
    def sourceFileForClass(className: String): VirtualFileRef =
      a.relations.definesClass(className).headOption.getOrElse {
        sys.error(s"Can't find source file for $className")
      }
    def isJavaClass(className: String) = sourceFileForClass(className).id.endsWith(".java")
    val (j, s) = a.apis.allInternalClasses.partition(isJavaClass)
    val c = a.stamps.allProducts
    val ext = a.apis.allExternals
    val jars = a.relations.allLibraryDeps.filter(_.id.endsWith(".jar"))
    val unreportedCount = a.infos.allInfos.values.map(_.getUnreportedProblems.length).sum
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
      case 1 => Some(s"1 $prefix$single")
      case x => Some(s"$x $prefix$plural")
    }

  lazy val dummyOutput: Output = new SingleOutput {
    def getOutputDirectory: Path = Paths.get("/tmp/dummy")
  }
}

private class MAnalysis(
    val stamps: Stamps,
    val apis: APIs,
    val relations: Relations,
    val infos: SourceInfos,
    val compilations: Compilations
) extends Analysis {
  def ++(o: Analysis): Analysis =
    new MAnalysis(
      stamps ++ o.stamps,
      apis ++ o.apis,
      relations ++ o.relations,
      infos ++ o.infos,
      compilations ++ o.compilations
    )

  def --(sources: Iterable[VirtualFileRef]): Analysis = {
    val newRelations = relations -- sources
    def keep[T](f: (Relations, T) => Set[_]): T => Boolean = f(newRelations, _).nonEmpty

    val classesInSrcs = sources.flatMap(relations.classNames)
    val newAPIs = apis.removeInternal(classesInSrcs).filterExt(keep(_.usesExternal(_)))
    val newStamps = stamps.filter(keep(_.produced(_)), sources, keep(_.usesLibrary(_)))
    val newInfos = infos -- sources
    new MAnalysis(newStamps, newAPIs, newRelations, newInfos, compilations)
  }

  def copy(
      stamps: Stamps,
      apis: APIs,
      relations: Relations,
      infos: SourceInfos,
      compilations: Compilations = compilations
  ): Analysis =
    new MAnalysis(stamps, apis, relations, infos, compilations)

  def addSource(
      src: VirtualFileRef,
      apis: Iterable[AnalyzedClass],
      stamp: XStamp,
      info: SourceInfo,
      nonLocalProducts: Iterable[NonLocalProduct],
      localProducts: Iterable[LocalProduct],
      internalDeps: Iterable[InternalDependency],
      externalDeps: Iterable[ExternalDependency],
      libraryDeps: Iterable[(VirtualFileRef, String, XStamp)]
  ): Analysis = {
    val newStamps = {
      val stamps0 = stamps.markSource(src, stamp)

      val stamps1 = nonLocalProducts.foldLeft(stamps0) { (acc, nonLocalProduct) =>
        acc.markProduct(nonLocalProduct.classFile, nonLocalProduct.classFileStamp)
      }

      val stamps2 = localProducts.foldLeft(stamps1) { (acc, localProduct) =>
        acc.markProduct(localProduct.classFile, localProduct.classFileStamp)
      }

      libraryDeps.foldLeft(stamps2) {
        case (acc, (toBinary, className, binStamp)) =>
          acc.markLibrary(toBinary, className, binStamp)
      }
    }

    val newAPIs = {
      val apis1 = apis.foldLeft(this.apis) { (acc, analyzedClass) =>
        acc.markInternalAPI(analyzedClass.name, analyzedClass)
      }

      externalDeps.foldLeft(apis1) { (acc, extDep) =>
        acc.markExternalAPI(extDep.targetProductClassName, extDep.targetClass)
      }
    }

    val products = nonLocalProducts.map(_.classFile) ++ localProducts.map(_.classFile)
    val classes = nonLocalProducts.map(p => p.className -> p.binaryClassName)

    val newRelations =
      relations.addSource(src, products, classes, internalDeps, externalDeps, libraryDeps)

    copy(newStamps, newAPIs, newRelations, infos.add(src, info))
  }

  override def equals(other: Any) = other match {
    // Note: Equality doesn't consider source infos or compilations.
    case o: MAnalysis => stamps == o.stamps && apis == o.apis && relations == o.relations
    case _            => false
  }

  override lazy val hashCode = (stamps :: apis :: relations :: Nil).hashCode
}
