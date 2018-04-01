/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbti.Problem
import java.io.File
import java.util.Optional

import xsbti.compile.analysis.{ ReadSourceInfos, SourceInfo }
import xsbti.semanticdb3.{ Role, SymbolOccurrence }

trait SourceInfos extends ReadSourceInfos {
  def ++(o: SourceInfos): SourceInfos
  def add(file: File, info: SourceInfo): SourceInfos
  def --(files: Iterable[File]): SourceInfos
  def groupBy[K](f: (File) => K): Map[K, SourceInfos]
  def allInfos: Map[File, SourceInfo]
}

object SourceInfos {
  def empty: SourceInfos = of(Map.empty)
  def of(m: Map[File, SourceInfo]): SourceInfos = new MSourceInfos(m)

  val emptyInfo: SourceInfo = makeInfo(Nil, Nil, Nil, Nil)
  def makeInfo(reported: Seq[Problem],
               unreported: Seq[Problem],
               mainClasses: Seq[String],
               symbolOccurrences: Seq[SymbolOccurrence]): SourceInfo =
    new UnderlyingSourceInfo(reported, unreported, mainClasses, symbolOccurrences)
  def merge(infos: Traversable[SourceInfos]): SourceInfos = (SourceInfos.empty /: infos)(_ ++ _)
}

private final class MSourceInfos(val allInfos: Map[File, SourceInfo]) extends SourceInfos {
  def ++(o: SourceInfos) = new MSourceInfos(allInfos ++ o.allInfos)
  def --(sources: Iterable[File]) = new MSourceInfos(allInfos -- sources)
  def groupBy[K](f: File => K): Map[K, SourceInfos] = allInfos groupBy (x => f(x._1)) map { x =>
    (x._1, new MSourceInfos(x._2))
  }
  def add(file: File, info: SourceInfo) = new MSourceInfos(allInfos + ((file, info)))

  override def get(file: File): SourceInfo = allInfos.getOrElse(file, SourceInfos.emptyInfo)
  override def getAllSourceInfos: java.util.Map[File, SourceInfo] = {
    import scala.collection.JavaConverters.mapAsJavaMapConverter
    mapAsJavaMapConverter(allInfos).asJava
  }
}

private final class UnderlyingSourceInfo(val reportedProblems: Seq[Problem],
                                         val unreportedProblems: Seq[Problem],
                                         val mainClasses: Seq[String],
                                         val symbolOccurrences: Seq[SymbolOccurrence])
    extends SourceInfo {
  override def getReportedProblems: Array[Problem] = reportedProblems.toArray
  override def getUnreportedProblems: Array[Problem] = unreportedProblems.toArray
  override def getMainClasses: Array[String] = mainClasses.toArray
  override def getSymbolOccurrences: Array[SymbolOccurrence] = symbolOccurrences.toArray
  override def getSymbolNameByPosition(line: Int, character: Int): Optional[String] =
    symbolOccurrences.find { symbolOccurrence =>
      val range = symbolOccurrence.range
      range.startLine() == line &&
      range.startCharacter() <= character &&
      character <= range.endCharacter()
    } match {
      case None     => Optional.empty[String]
      case Some(so) => Optional.of(so.symbol)
    }
  override def getSymbolDefinition(fullName: String): java.util.Set[SymbolOccurrence] = {
    val javaSet = new java.util.HashSet[SymbolOccurrence]()
    symbolOccurrences
      .filter(
        symbolOccurrence =>
          symbolOccurrence.role == Role.DEFINITION &&
            symbolOccurrence.symbol == fullName)
      .foreach(javaSet.add)
    javaSet
  }
}
