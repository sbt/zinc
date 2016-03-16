/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package internal
package inc

import sbt.internal.inc.Analysis.{ LocalProduct, NonLocalProduct }
import java.io.File
import sbt.internal.util.Relation

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
  def empty(nameHashing: Boolean): Analysis = new MAnalysis(Stamps.empty, APIs.empty,
    Relations.empty(nameHashing = nameHashing), SourceInfos.empty, Compilations.empty)

  /** Merge multiple analysis objects into one. Deps will be internalized as needed. */
  def merge(analyses: Traversable[Analysis]): Analysis = {
    if (analyses.exists(_.relations.nameHashing))
      throw new IllegalArgumentException("Merging of Analyses that have" +
        "`relations.memberRefAndInheritanceDeps` set to `true` is not supported.")

    // Merge the Relations, internalizing deps as needed.
    val mergedSrcProd = Relation.merge(analyses map { _.relations.srcProd })
    val mergedBinaryDep = Relation.merge(analyses map { _.relations.binaryDep })
    val mergedClasses = Relation.merge(analyses map { _.relations.classes })

    val stillInternal = Relation.merge(analyses map { _.relations.direct.internal })
    val (internalized, stillExternal) = Relation.merge(analyses map { _.relations.direct.external }) partition { case (a, b) => mergedClasses._2s.contains(b) }
    val internalizedFiles = Relation.reconstruct(internalized.forwardMap mapValues { _ flatMap mergedClasses.reverse })
    val mergedInternal = stillInternal ++ internalizedFiles

    val stillInternalPI = Relation.merge(analyses map { _.relations.publicInherited.internal })
    val (internalizedPI, stillExternalPI) = Relation.merge(analyses map { _.relations.publicInherited.external }) partition { case (a, b) => mergedClasses._2s.contains(b) }
    val internalizedFilesPI = Relation.reconstruct(internalizedPI.forwardMap mapValues { _ flatMap mergedClasses.reverse })
    val mergedInternalPI = stillInternalPI ++ internalizedFilesPI

    val mergedRelations = Relations.make(
      mergedSrcProd,
      mergedBinaryDep,
      Relations.makeSource(mergedInternal, stillExternal),
      Relations.makeSource(mergedInternalPI, stillExternalPI),
      mergedClasses
    )

    // Merge the APIs, internalizing APIs for targets of dependencies we internalized above.
    val concatenatedAPIs = (APIs.empty /: (analyses map { _.apis }))(_ ++ _)
    val stillInternalAPIs = concatenatedAPIs.internal
    val (internalizedAPIs, stillExternalAPIs) = concatenatedAPIs.external partition { x: (String, AnalyzedClass) =>
      internalized._2s.contains(x._1)
    }
    val mergedAPIs = APIs(stillInternalAPIs ++ internalizedAPIs, stillExternalAPIs)

    val mergedStamps = Stamps.merge(analyses map { _.stamps })
    val mergedInfos = SourceInfos.merge(analyses map { _.infos })
    val mergedCompilations = Compilations.merge(analyses map { _.compilations })

    new MAnalysis(mergedStamps, mergedAPIs, mergedRelations, mergedInfos, mergedCompilations)
  }

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
      val jars = a.relations.allBinaryDeps.filter(_.getName.endsWith(".jar"))
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
      val newStamps = stamps.filter(keep(_ produced _), sources, keep(_ usesBinary _))
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
      case (tmpApis, ed: ExternalDependency) => tmpApis.markExternalAPI(ed.targetBinaryClassName, ed.targetClass)
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
