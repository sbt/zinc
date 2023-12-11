/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
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
  ): SourceInfo =
    new UnderlyingSourceInfo(reported, unreported, mainClasses)

  def merge(infos: Traversable[SourceInfos]): SourceInfos =
    infos.foldLeft(SourceInfos.empty)(_ ++ _)

  def merge1(info1: SourceInfo, info2: SourceInfo) = {
    makeInfo(
      mergeProblems(info1.getReportedProblems, info2.getReportedProblems),
      mergeProblems(info1.getUnreportedProblems, info2.getUnreportedProblems),
      (info1.getMainClasses ++ info2.getMainClasses).distinct,
    )
  }

  def mergeProblems(ps1: Seq[Problem], ps2: Seq[Problem]) = {
    distinctBy(ps1 ++ ps2)(problemKey)
  }

  // Seq#distinctBy is 2.13+ only
  def distinctBy[A, B](xs: Seq[A])(f: A => B) = xs.groupBy(f).map(_._2.head).toSeq

  def problemKey(p: Problem) = {
    (
      p.category,
      p.severity.ordinal,
      p.message,
      p.position.sourcePath.orElse(""),
      p.position.offset.orElse(0),
      p.position.startOffset.orElse(0),
      p.position.endOffset.orElse(0)
    )
  }
}

private final class MSourceInfos(val allInfos: Map[VirtualFileRef, SourceInfo])
    extends SourceInfos {

  def ++(o: SourceInfos) = {
    var infos = new scala.collection.immutable.HashMap[VirtualFileRef, SourceInfo]
    (allInfos.keySet ++ o.allInfos.keySet).foreach { file =>
      val lhs = allInfos.getOrElse(file, null)
      val rhs = o.allInfos.getOrElse(file, null)
      val res = if (lhs == null) rhs else if (rhs == null) lhs else SourceInfos.merge1(lhs, rhs)
      infos = infos.updated(file, res)
    }
    new MSourceInfos(infos)
  }

  def --(sources: Iterable[VirtualFileRef]) = new MSourceInfos(allInfos -- sources)

  def groupBy[K](f: VirtualFileRef => K): Map[K, SourceInfos] =
    allInfos.groupBy(x => f(x._1)).map(x => (x._1, new MSourceInfos(x._2)))

  def add(file: VirtualFileRef, info: SourceInfo) = {
    val info0 = allInfos.getOrElse(file, null)
    val infos = allInfos.updated(file, if (info0 == null) info else SourceInfos.merge1(info0, info))
    new MSourceInfos(infos)
  }

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
