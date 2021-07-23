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
import java.nio.file.{ Path, Paths }

import org.scalacheck._, Arbitrary._, Gen._, Prop._
import sbt.internal.inc.APIs.emptyModifiers
import sbt.internal.util.Relation
import xsbti.api._
import xsbti.{ UseScope, VirtualFileRef }
import xsbti.api.DefinitionType.{ ClassDef, Module }
import xsbti.api.DependencyContext._
import xsbti.compile.analysis.{ Stamp => AStamp }

import scala.collection.immutable.TreeMap
import scala.reflect.ClassTag
import scala.util.Try

/**
 * ScalaCheck generators for Analysis objects and their substructures.
 * Fairly complex, as Analysis has interconnected state that can't be
 * independently generated.
 */
object AnalysisGenerators {
  val RootFilePath: Path = Paths.get("/tmp/localProject")

  // We restrict sizes, otherwise the generated Analysis objects get huge and the tests take a long time.
  val maxSources = 10 // Max number of source files.
  val maxRelatives = 10 // Max number of things that a source x can relate to in a single Relation.
  val maxPathSegmentLen = 10 // Max number of characters in a path segment.
  val maxPathLen = 6 // Max number of path segments in a path.

  // Ensure that we generate unique class names and file paths every time.
  // Using repeated strings may lead to all sorts of undesirable interactions.
  val used1 = scala.collection.mutable.Set.empty[String]
  val used2 = scala.collection.mutable.Set.empty[String]

  def throws(p: => Prop): Prop = Try(p).fold(_ => proved, _ => falsified)

  // When using `retryUntil`, the condition is actually tested twice (see implementation in ScalaCheck),
  // which is why we need to insert twice the element.
  // If the element is present in both sets, then it has already been used.
  def unique[T](g: Gen[T]) = g.retryUntil(o => used1.add(o.toString) || used2.add(o.toString))

  def identifier: Gen[String] = sized(size => resize(Math.max(size, 3), Gen.identifier))

  def genFilePathSegment: Gen[String] =
    for {
      n <- choose(3, maxPathSegmentLen) // Segments have at least 3 characters.
      c <- alphaChar
      cs <- listOfN(n - 1, alphaNumChar)
    } yield (c :: cs).mkString

  def genFile: Gen[File] = {
    for {
      n <- choose(2, maxPathLen) // Paths have at least 2 segments.
      path <- listOfN(n, genFilePathSegment)
    } yield new File(s"$RootFilePath/" + path.mkString("/"))
  }

  def genStamp: Gen[AStamp] = const(EmptyStamp)

  def zipMap[A, B](a: Seq[A], b: Seq[B]): Map[A, B] = a.zip(b).toMap

  def genStamps(rel: Relations): Gen[Stamps] = {
    import VirtualFileUtil._

    def stamp(xs: Iterable[VirtualFileRef]) =
      for (stamps <- listOfN(xs.size, genStamp)) yield TreeMap(xs.toList.zip(stamps): _*)

    for {
      prods <- stamp(rel.allProducts)
      srcs <- stamp(rel.allSources)
      libs <- stamp(rel.allLibraryDeps)
    } yield Stamps(prods, srcs, libs)
  }

  private[this] def arr[A <: AnyRef: ClassTag] = new Array[A](0)
  private[this] val noTpe = lzy[Type](EmptyType.of())
  private[this] val noMods = emptyModifiers
  private[this] val noStruct = lzy(Structure.of(lzy(arr), lzy(arr), lzy(arr)))

  // We need "proper" definitions with specific class names,
  // as groupBy use these to pick a representative top-level class when splitting.
  private[this] def makeClassLike(name: String, defnTpe: DefinitionType): ClassLike =
    ClassLike.of(name, Public.of(), noMods, arr, defnTpe, noTpe, noStruct, arr, arr, true, arr)

  private[this] def makeCompanions(name: String): Companions =
    Companions.of(makeClassLike(name, ClassDef), makeClassLike(name, Module))

  private[this] def lzy[T <: AnyRef](x: T) = SafeLazyProxy.strict(x)

  def genNameHash(name: String): Gen[NameHash] =
    for (scope <- oneOf(UseScope.values()))
      yield NameHash.of(name, scope, (name, scope).hashCode())

  def genClass(name: String): Gen[AnalyzedClass] =
    for {
      startTime <- arbitrary[Long]
      apiHash <- arbitrary[Int]
      hasMacro <- arbitrary[Boolean]
      nameHash <- genNameHash(name)
      provenance <- arbitrary[String]
    } yield {
      AnalyzedClass.of(
        startTime,
        name,
        SafeLazyProxy(makeCompanions(name)),
        apiHash,
        Array(nameHash),
        hasMacro,
        apiHash, // The default is to use the public API hash
        provenance
      )
    }

  def genClasses(defns: Seq[String]): Gen[Seq[AnalyzedClass]] =
    Gen.sequence[List[AnalyzedClass], AnalyzedClass](defns.map(genClass))

  def genAPIs(rel: Relations): Gen[APIs] = {
    val internal = rel.internalClassDep._1s.toList.sorted ++ rel.internalClassDep._2s.toList.sorted
    val external = rel.allExternalDeps.toList.sorted
    for {
      internalSources <- genClasses(internal)
      externalSources <- genClasses(external)
    } yield APIs(zipMap(internal, internalSources), zipMap(external, externalSources))
  }

  def genVirtualFileRefRelation[T](
      g: Gen[T]
  )(srcs: List[VirtualFileRef]): Gen[Relation[VirtualFileRef, T]] =
    for {
      n <- choose(1, maxRelatives)
      entries <- listOfN(srcs.length, containerOfN[Set, T](n, g))
    } yield Relation.reconstruct(zipMap(srcs, entries))

  val genStringRelation = genVirtualFileRefRelation(unique(identifier)) _
  val genFileVORefRelation = genVirtualFileRefRelation(unique(genFileVRef)) _

  def rel[A, B](a: Seq[A], b: Seq[B]): Relation[A, B] =
    Relation.reconstruct(zipMap(a, b).mapValues(Set(_)).toMap)

  def genStringStringRelation(num: Int): Gen[Relation[String, String]] =
    for {
      n <- choose(1, if (num == 0) 1 else num)
      fwd <- listOfN(n, unique(identifier))
      prv <- listOfN(n, unique(identifier))
    } yield rel(fwd, prv)

  def genRClassDependencies(classNames: List[String]): Gen[Relations.ClassDependencies] =
    for {
      internal <- listOfN(classNames.length, someOf(classNames))
      external <- listOfN(classNames.length, someOf(classNames))
    } yield {
      def toForwardMap(targets: Seq[scala.collection.Seq[String]]): Map[String, Set[String]] =
        classNames.zip(targets.map(_.toSet)).map { case (a, b) => (a, b - a) }.toMap
      Relations.makeClassDependencies(
        Relation.reconstruct(toForwardMap(internal)),
        Relation.reconstruct(toForwardMap(external))
      )
    }

  private[inc] def genSubRClassDependencies(
      src: Relations.ClassDependencies
  ): Gen[Relations.ClassDependencies] =
    for {
      internal <- someOf(src.internal.all.toList)
      external <- someOf(src.external.all.toList)
    } yield Relations.makeClassDependencies(Relation.empty ++ internal, Relation.empty ++ external)

  def genScalaName: Gen[String] =
    Gen.listOf(Gen.oneOf(Gen.choose('!', 'Z'), Gen.const('\n'))).map(_.toString())

  def genUsedName(namesGen: Gen[String] = genScalaName): Gen[UsedName] =
    for (name <- namesGen; scopes <- Gen.someOf(UseScope.values()))
      yield UsedName(name, UseScope.Default +: scopes)

  def genUsedNames(classNames: Seq[String]): Gen[Relations.UsedNames] =
    for (allNames <- listOfN(classNames.length, containerOf[Set, UsedName](genUsedName())))
      yield UsedNames.fromMultiMap(zipMap(classNames, allNames))

  def genFileVRef: Gen[VirtualFileRef] = genFile.map(x => VirtualFileRef.of(x.toPath.toString))

  def genRelationsNameHashing: Gen[Relations] =
    for {
      numSrcs <- choose(0, maxSources)
      srcs <- listOfN(numSrcs, genFileVRef)
      srcProd <- genFileVORefRelation(srcs)
      libraryDep <- genFileVORefRelation(srcs)
      libraryClassName <- genStringRelation(libraryDep._2s.toList)
      productClassName <- genStringStringRelation(numSrcs)
      classNames = productClassName._1s.toList
      memberRef <- genRClassDependencies(classNames)
      inheritance <- genSubRClassDependencies(memberRef)
      localInheritance <- genSubRClassDependencies(memberRef)
      internal <- InternalDependencies(
        Map(
          DependencyByMemberRef -> memberRef.internal,
          DependencyByInheritance -> inheritance.internal,
          LocalDependencyByInheritance -> localInheritance.internal
        )
      )
      external <- ExternalDependencies(
        Map(
          DependencyByMemberRef -> memberRef.external,
          DependencyByInheritance -> inheritance.external,
          LocalDependencyByInheritance -> localInheritance.external
        )
      )
      classes = rel(srcs, classNames)
      names <- genUsedNames(classNames)
    } yield Relations.make(
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
    for {
      rels <- genRelationsNameHashing
      stamps <- genStamps(rels)
      apis <- genAPIs(rels)
    } yield new MAnalysis(stamps, apis, rels, SourceInfos.empty, Compilations.empty)
}
