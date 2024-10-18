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

package xsbti

import java.io.File
import java.nio.file.Path
import java.{ util => ju }
import ju.Optional
import xsbti.api.{ ClassLike, DependencyContext }
import xsbti.compile.analysis.ReadSourceInfos

import scala.collection.mutable.ArrayBuffer

class TestCallback extends AnalysisCallback3 {
  case class TestUsedName(name: String, scopes: ju.EnumSet[UseScope])

  val classDependencies = new ArrayBuffer[(String, String, DependencyContext)]
  val binaryDependencies =
    new ArrayBuffer[(Path, String, String, DependencyContext)]
  val productClassesToSources =
    scala.collection.mutable.Map.empty[Path, VirtualFileRef]
  val usedNamesAndScopes =
    scala.collection.mutable.Map.empty[String, Set[TestUsedName]].withDefaultValue(Set.empty)
  val classNames =
    scala.collection.mutable.Map
      .empty[VirtualFileRef, Set[(String, String)]]
      .withDefaultValue(Set.empty)
  val apis: scala.collection.mutable.Map[VirtualFileRef, Set[ClassLike]] =
    scala.collection.mutable.Map.empty

  def usedNames = usedNamesAndScopes.mapValues(_.map(_.name))

  override def startSource(source: File): Unit = ???
  override def startSource(source: VirtualFile): Unit = {
    assert(
      !apis.contains(source),
      s"The startSource can be called only once per source file: $source"
    )
    apis(source) = Set.empty
  }

  def classDependency(
      onClassName: String,
      sourceClassName: String,
      context: DependencyContext
  ): Unit = {
    if (onClassName != sourceClassName)
      classDependencies += ((onClassName, sourceClassName, context))
    ()
  }

  override def binaryDependency(
      classFile: File,
      onBinaryClassName: String,
      fromClassName: String,
      fromSourceFile: File,
      context: DependencyContext
  ): Unit = ???

  override def binaryDependency(
      onBinary: Path,
      onBinaryClassName: String,
      fromClassName: String,
      fromSourceFile: VirtualFileRef,
      context: DependencyContext
  ): Unit = {
    binaryDependencies += ((onBinary, onBinaryClassName, fromClassName, context))
    ()
  }

  override def generatedNonLocalClass(
      sourceFile: File,
      classFile: File,
      binaryClassName: String,
      srcClassName: String
  ): Unit = ???

  override def generatedNonLocalClass(
      sourceFile: VirtualFileRef,
      classFile: Path,
      binaryClassName: String,
      srcClassName: String
  ): Unit = {
    productClassesToSources += ((classFile, sourceFile))
    classNames(sourceFile) += ((srcClassName, binaryClassName))
    ()
  }

  override def generatedLocalClass(
      sourceFile: File,
      classFile: File
  ): Unit = ???

  override def generatedLocalClass(
      sourceFile: VirtualFileRef,
      classFile: Path
  ): Unit = {
    productClassesToSources += ((classFile, sourceFile))
    ()
  }

  def usedName(className: String, name: String, scopes: ju.EnumSet[UseScope]): Unit =
    usedNamesAndScopes(className) += TestUsedName(name, scopes)

  override def api(source: File, api: ClassLike): Unit = ???

  override def api(source: VirtualFileRef, api: ClassLike): Unit = {
    apis(source) += api
    ()
  }

  override def mainClass(source: File, className: String): Unit = ()

  override def mainClass(source: VirtualFileRef, className: String): Unit = ()

  override def enabled(): Boolean = true

  override def problem(
      category: String,
      pos: xsbti.Position,
      message: String,
      severity: xsbti.Severity,
      reported: Boolean,
  ): Unit = ()

  override def problem2(
      category: String,
      pos: Position,
      msg: String,
      severity: Severity,
      reported: Boolean,
      rendered: Optional[String],
      diagnosticCode: Optional[xsbti.DiagnosticCode],
      diagnosticRelatedInformation: ju.List[xsbti.DiagnosticRelatedInformation],
      actions: ju.List[xsbti.Action],
  ): Unit = ()

  override def dependencyPhaseCompleted(): Unit = {}

  override def apiPhaseCompleted(): Unit = {}

  override def classesInOutputJar(): ju.Set[String] = ju.Collections.emptySet()

  override def isPickleJava: Boolean = false

  override def getPickleJarPair = Optional.empty()

  override def toVirtualFile(path: Path): VirtualFile = {
    throw new UnsupportedOperationException("This method should not be called in tests")
  }

  override def getSourceInfos: ReadSourceInfos = new TestSourceInfos
}

object TestCallback {
  case class ExtractedClassDependencies(
      memberRef: Map[String, Set[String]],
      inheritance: Map[String, Set[String]],
      localInheritance: Map[String, Set[String]]
  )
  object ExtractedClassDependencies {
    def fromPairs(
        memberRefPairs: Seq[(String, String)],
        inheritancePairs: Seq[(String, String)],
        localInheritancePairs: Seq[(String, String)]
    ): ExtractedClassDependencies = {
      ExtractedClassDependencies(
        pairsToMultiMap(memberRefPairs),
        pairsToMultiMap(inheritancePairs),
        pairsToMultiMap(localInheritancePairs)
      )
    }

    private def pairsToMultiMap[A, B](pairs: Seq[(A, B)]): Map[A, Set[B]] = {
      import scala.collection.mutable.{ HashMap, MultiMap }
      val emptyMultiMap = new HashMap[A, scala.collection.mutable.Set[B]] with MultiMap[A, B]
      val multiMap = pairs.foldLeft(emptyMultiMap) {
        case (acc, (key, value)) =>
          acc.addBinding(key, value)
      }
      // convert all collections to immutable variants
      multiMap.toMap.mapValues(_.toSet).toMap.withDefaultValue(Set.empty)
    }
  }
}
