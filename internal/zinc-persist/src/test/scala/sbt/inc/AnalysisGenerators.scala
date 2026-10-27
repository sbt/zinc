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

import java.io.File
import java.nio.file.{ Path, Paths }

import hedgehog.{ sbt as _, * }
import hedgehog.predef.traverse
import sbt.internal.inc.APIs.emptyModifiers
import sbt.internal.util.Relation
import xsbti.api.*
import xsbti.{ UseScope, VirtualFileRef }
import xsbti.api.DefinitionType.{ ClassDef, Module }
import xsbti.api.DependencyContext.*
import xsbti.compile.analysis.Stamp as AStamp

import scala.collection.immutable.TreeMap
import scala.reflect.ClassTag
import scala.util.Try

/**
 * HedgeHog generators for Analysis objects and their substructures.
 * Fairly complex, as Analysis has interconnected state that can't be
 * independently generated.
 */
object AnalysisGenerators:
  val RootFilePath: Path = Paths.get("/tmp/localProject")

  // We restrict sizes, otherwise the generated Analysis objects get huge and the tests take a long time.
  val maxSources = 10 // Max number of source files.
  val maxRelatives = 10 // Max number of things that a source x can relate to in a single Relation.
  val maxPathSegmentLen = 10 // Max number of characters in a path segment.
  val maxPathLen = 6 // Max number of path segments in a path.

  // Ensure that we generate unique class names and file paths every time.
  // Using repeated strings may lead to all sorts of undesirable interactions.
  // A suffix that tells where a value was generated keeps it apart from every other one.
  private def uniqueString(s: String, where: String): String = s"${s}_$where"

  private def uniqueFileVRef(f: VirtualFileRef, where: String): VirtualFileRef =
    VirtualFileRef.of(uniqueString(f.id, where))

  def throws(p: => Result): Result = Try(p).fold(_ => Result.success, _ => Result.failure)

  def identifier: Gen[String] =
    for
      c <- Gen.lower
      cs <- Gen.string(Gen.alphaNum, Range.linear(2, 10))
    yield s"$c$cs"

  def genFilePathSegment: Gen[String] =
    for
      n <- Gen.int(Range.constant(3, maxPathSegmentLen)) // Segments have at least 3 characters.
      c <- Gen.alpha
      cs <- Gen.list(Gen.alphaNum, Range.singleton(n - 1))
    yield (c :: cs).mkString

  def genFile: Gen[File] =
    for
      n <- Gen.int(Range.constant(2, maxPathLen)) // Paths have at least 2 segments.
      path <- Gen.list(genFilePathSegment, Range.singleton(n))
    yield new File(s"$RootFilePath/" + path.mkString("/"))

  def genStamp: Gen[AStamp] = Gen.constant(EmptyStamp)

  def zipMap[A, B](a: Seq[A], b: Seq[B]): Map[A, B] = a.zip(b).toMap

  def genStamps(rel: Relations): Gen[Stamps] =
    import VirtualFileUtil.*

    def stamp(xs: Iterable[VirtualFileRef]) =
      for stamps <- Gen.list(genStamp, Range.singleton(xs.size))
      yield TreeMap(xs.toList.zip(stamps)*)

    for
      prods <- stamp(rel.allProducts)
      srcs <- stamp(rel.allSources)
      libs <- stamp(rel.allLibraryDeps)
    yield Stamps(prods, srcs, libs)

  private def arr[A <: AnyRef: ClassTag] = new Array[A](0)
  private val noTpe = lzy[Type](EmptyType.of())
  private val noMods = emptyModifiers
  private val noStruct = lzy(Structure.of(lzy(arr), lzy(arr), lzy(arr)))

  // We need "proper" definitions with specific class names,
  // as groupBy use these to pick a representative top-level class when splitting.
  private def makeClassLike(name: String, defnTpe: DefinitionType): ClassLike =
    ClassLike.of(name, Public.of(), noMods, arr, defnTpe, noTpe, noStruct, arr, arr, true, arr)

  private def makeCompanions(name: String): Companions =
    Companions.of(makeClassLike(name, ClassDef), makeClassLike(name, Module))

  private def lzy[T <: AnyRef](x: T) = SafeLazyProxy.strict(x)

  def genNameHash(name: String): Gen[NameHash] =
    for scope <- Gen.elementUnsafe(UseScope.values().toList)
    yield NameHash.of(name, scope, (name, scope).hashCode())

  def genClass(name: String): Gen[AnalyzedClass] =
    for
      startTime <- Gen.long(Range.linearFrom(0L, Long.MinValue, Long.MaxValue))
      apiHash <- Gen.int(Range.linearFrom(0, Int.MinValue, Int.MaxValue))
      hasMacro <- Gen.boolean
      nameHash <- genNameHash(name)
      provenance <- Gen.string(Gen.unicode, Range.linear(0, 100))
    yield AnalyzedClass.of(
      startTime,
      name,
      SafeLazyProxy(makeCompanions(name)),
      apiHash,
      Array(nameHash),
      hasMacro,
      apiHash, // The default is to use the public API hash
      provenance
    )

  def genClasses(defns: Seq[String]): Gen[Seq[AnalyzedClass]] =
    traverse(defns.toList)(genClass).map(_.toSeq)

  def genAPIs(rel: Relations): Gen[APIs] =
    val internal = rel.internalClassDep._1s.toList.sorted ++ rel.internalClassDep._2s.toList.sorted
    val external = rel.allExternalDeps.toList.sorted
    for
      internalSources <- genClasses(internal)
      externalSources <- genClasses(external)
    yield APIs(zipMap(internal, internalSources), zipMap(external, externalSources))

  def genVirtualFileRefRelation[T](g: Gen[T], unique: (T, String) => T, tag: String)(
      srcs: List[VirtualFileRef]
  ): Gen[Relation[VirtualFileRef, T]] =
    for
      n <- Gen.int(Range.constant(1, maxRelatives))
      entries <- Gen.list(Gen.list(g, Range.singleton(n)), Range.singleton(srcs.length))
    yield
      val sets = entries.zipWithIndex.map { (es, i) =>
        es.zipWithIndex.map((e, j) => unique(e, s"$tag${i}_$j")).toSet
      }
      Relation.reconstruct(zipMap(srcs, sets))

  def genStringRelation(tag: String) = genVirtualFileRefRelation(identifier, uniqueString, tag)
  def genFileVORefRelation(tag: String) =
    genVirtualFileRefRelation(genFileVRef, uniqueFileVRef, tag)

  def rel[A, B](a: Seq[A], b: Seq[B]): Relation[A, B] =
    Relation.reconstruct(zipMap(a, b).view.mapValues(Set(_)).toMap)

  def genStringStringRelation(num: Int): Gen[Relation[String, String]] =
    for
      n <- Gen.int(Range.constant(1, if num == 0 then 1 else num))
      fwd <- Gen.list(identifier, Range.singleton(n))
      prv <- Gen.list(identifier, Range.singleton(n))
    yield rel(
      fwd.zipWithIndex.map((s, i) => uniqueString(s, s"f$i")),
      prv.zipWithIndex.map((s, i) => uniqueString(s, s"p$i"))
    )

  /** A subset of `xs`, like ScalaCheck's `someOf`. */
  private def someOf[A](xs: Seq[A]): Gen[Seq[A]] =
    Gen
      .list(Gen.boolean, Range.singleton(xs.size))
      .map(keep => xs.zip(keep).collect { case (x, true) => x })

  def genRClassDependencies(classNames: List[String]): Gen[Relations.ClassDependencies] =
    for
      internal <- Gen.list(someOf(classNames), Range.singleton(classNames.length))
      external <- Gen.list(someOf(classNames), Range.singleton(classNames.length))
    yield
      def toForwardMap(targets: Seq[scala.collection.Seq[String]]): Map[String, Set[String]] =
        classNames.zip(targets.map(_.toSet)).map { case (a, b) => (a, b - a) }.toMap
      Relations.makeClassDependencies(
        Relation.reconstruct(toForwardMap(internal)),
        Relation.reconstruct(toForwardMap(external))
      )

  private[inc] def genSubRClassDependencies(
      src: Relations.ClassDependencies
  ): Gen[Relations.ClassDependencies] =
    for
      internal <- someOf(src.internal.all.toList)
      external <- someOf(src.external.all.toList)
    yield Relations.makeClassDependencies(Relation.empty ++ internal, Relation.empty ++ external)

  def genScalaName: Gen[String] =
    Gen
      .list(Gen.choice1(Gen.char('!', 'Z'), Gen.constant('\n')), Range.linear(0, 100))
      .map(_.mkString)

  def genUsedName(namesGen: Gen[String] = genScalaName): Gen[UsedName] =
    for name <- namesGen; scopes <- someOf(UseScope.values().toSeq)
    yield UsedName(name, UseScope.Default +: scopes)

  def genUsedNames(classNames: Seq[String]): Gen[Relations.UsedNames] =
    for
      allNames <- Gen.list(
        Gen.list(genUsedName(), Range.linear(0, 100)).map(_.toSet),
        Range.singleton(classNames.length)
      )
    yield UsedNames.fromMultiMap(zipMap(classNames, allNames))

  def genFileVRef: Gen[VirtualFileRef] = genFile.map(x => VirtualFileRef.of(x.toPath.toString))

  def genRelationsNameHashing: Gen[Relations] =
    for
      numSrcs <- Gen.int(Range.constant(0, maxSources))
      srcs <- Gen.list(genFileVRef, Range.singleton(numSrcs))
      srcProd <- genFileVORefRelation("prod")(srcs)
      libraryDep <- genFileVORefRelation("lib")(srcs)
      libraryClassName <- genStringRelation("libClass")(libraryDep._2s.toList)
      productClassName <- genStringStringRelation(numSrcs)
      classNames = productClassName._1s.toList
      memberRef <- genRClassDependencies(classNames)
      inheritance <- genSubRClassDependencies(memberRef)
      localInheritance <- genSubRClassDependencies(memberRef)
      macroExpansion <- genSubRClassDependencies(memberRef)
      internal = InternalDependencies(
        Map(
          DependencyByMemberRef -> memberRef.internal,
          DependencyByInheritance -> inheritance.internal,
          LocalDependencyByInheritance -> localInheritance.internal,
          DependencyByMacroExpansion -> macroExpansion.internal,
        )
      )
      external = ExternalDependencies(
        Map(
          DependencyByMemberRef -> memberRef.external,
          DependencyByInheritance -> inheritance.external,
          LocalDependencyByInheritance -> localInheritance.external,
          DependencyByMacroExpansion -> macroExpansion.external,
        )
      )
      classes = rel(srcs, classNames)
      names <- genUsedNames(classNames)
    yield Relations.make(
      srcProd,
      libraryDep,
      libraryClassName,
      internal,
      external,
      classes,
      names,
      productClassName
    )

  def genAnalysis: Gen[Analysis] =
    for
      rels <- genRelationsNameHashing
      stamps <- genStamps(rels)
      apis <- genAPIs(rels)
    yield new MAnalysis(stamps, apis, rels, SourceInfos.empty, Compilations.empty)
end AnalysisGenerators
