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

import xsbti.{ Problem, VirtualFileRef }

import xsbti.compile.analysis.{ ReadSourceInfos, SourceInfo }

trait SourceInfos extends ReadSourceInfos {
  def ++(o: SourceInfos): SourceInfos
  def add(file: VirtualFileRef, info: SourceInfo): SourceInfos
  def --(files: Iterable[VirtualFileRef]): SourceInfos
  def groupBy[K](f: (VirtualFileRef) => K): Map[K, SourceInfos]
  def allInfos: Map[VirtualFileRef, SourceInfo]
}

object SourceInfos {
  def empty: SourceInfos = of(Map.empty)
  def of(m: Map[VirtualFileRef, SourceInfo]): SourceInfos = new MSourceInfos(m)

  val emptyInfo: SourceInfo = makeInfo(Nil, Nil, Nil)

  def makeInfo(
      reported: Seq[Problem],
      unreported: Seq[Problem],
      mainClasses: Seq[String]
  ): SourceInfo = new UnderlyingSourceInfo(reported, unreported, mainClasses)

  def merge(infos: Traversable[SourceInfos]): SourceInfos =
    infos.foldLeft(SourceInfos.empty)(_ ++ _)

}

private final class MSourceInfos(val allInfos: Map[VirtualFileRef, SourceInfo])
    extends SourceInfos {
  def ++(o: SourceInfos) = new MSourceInfos(allInfos ++ o.allInfos)
  def --(sources: Iterable[VirtualFileRef]) = new MSourceInfos(allInfos -- sources)

  def groupBy[K](f: VirtualFileRef => K): Map[K, SourceInfos] =
    allInfos.groupBy(x => f(x._1)).map(x => (x._1, new MSourceInfos(x._2)))

  def add(file: VirtualFileRef, info: SourceInfo) = new MSourceInfos(allInfos + ((file, info)))

  override def get(file: VirtualFileRef): SourceInfo =
    allInfos.getOrElse(file, SourceInfos.emptyInfo)

  override def getAllSourceInfos: java.util.Map[VirtualFileRef, SourceInfo] = {
    import scala.collection.JavaConverters.mapAsJavaMapConverter
    mapAsJavaMapConverter(allInfos).asJava
  }

}

private final class UnderlyingSourceInfo(
    val reportedProblems: Seq[Problem],
    val unreportedProblems: Seq[Problem],
    val mainClasses: Seq[String]
) extends SourceInfo {
  override def getReportedProblems: Array[Problem] = reportedProblems.toArray
  override def getUnreportedProblems: Array[Problem] = unreportedProblems.toArray
  override def getMainClasses: Array[String] = mainClasses.toArray

  override def toString: String =
    s"SourceInfo($reportedProblems, $unreportedProblems, $mainClasses)"

}
