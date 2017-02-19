package sbt
package internal
package inc

import java.io.File

import org.scalacheck._
import Arbitrary._
import Gen._

import sbt.internal.util.Relation
import xsbti.api._
import xsbti.api.DependencyContext._

/**
 * Scalacheck generators for Analysis objects and their substructures.
 * Fairly complex, as Analysis has interconnected state that can't be
 * independently generated.
 */
object TestCaseGenerators {
  // We restrict sizes, otherwise the generated Analysis objects get huge and the tests take a long time.
  val maxSources = 10 // Max number of source files.
  val maxRelatives = 10 // Max number of things that a source x can relate to in a single Relation.
  val maxPathSegmentLen = 10 // Max number of characters in a path segment.
  val maxPathLen = 6 // Max number of path segments in a path.

  // Ensure that we generate unique class names and file paths every time.
  // Using repeated strings may lead to all sorts of undesirable interactions.
  val used1 = scala.collection.mutable.Set.empty[String]
  val used2 = scala.collection.mutable.Set.empty[String]

  // When using `retryUntil`, the condition is actually tested twice (see implementation in ScalaCheck),
  // which is why we need to insert twice the element.
  // If the element is present in both sets, then it has already been used.
  def unique[T](g: Gen[T]) = g retryUntil { o: T =>
    if (used1.add(o.toString))
      true
    else
      used2.add(o.toString)
  }

  def identifier: Gen[String] = sized { size =>
    resize(Math.max(size, 3), Gen.identifier)
  }

  def genFilePathSegment: Gen[String] = for {
    n <- choose(3, maxPathSegmentLen) // Segments have at least 3 characters.
    c <- alphaChar
    cs <- listOfN(n - 1, alphaNumChar)
  } yield (c :: cs).mkString

  def genFile: Gen[File] = for {
    n <- choose(2, maxPathLen) // Paths have at least 2 segments.
    path <- listOfN(n, genFilePathSegment)
  } yield new File("/temp/" + path.mkString("/"))

  def genStamp: Gen[Stamp] = for {
    b <- oneOf(true, false)
  } yield new Exists(b)

  def zipMap[A, B](a: Seq[A], b: Seq[B]): Map[A, B] = (a zip b).toMap

  def genStamps(rel: Relations): Gen[Stamps] = {
    val prod = rel.allProducts.toList
    val src = rel.allSources.toList
    val bin = rel.allLibraryDeps.toList
    for {
      prodStamps <- listOfN(prod.length, genStamp)
      srcStamps <- listOfN(src.length, genStamp)
      binStamps <- listOfN(bin.length, genStamp)
    } yield Stamps(zipMap(prod, prodStamps), zipMap(src, srcStamps), zipMap(bin, binStamps))
  }

  private[this] val emptyStructure = new Structure(lzy(Array()), lzy(Array()), lzy(Array()))

  // We need "proper" definitions with specific class names, as groupBy use these to pick a representative top-level class when splitting.
  private[this] def makeClassLike(name: String, definitionType: DefinitionType): ClassLike =
    new ClassLike(name, new Public(), APIs.emptyModifiers, Array(),
      definitionType, lzy(new EmptyType()), lzy(emptyStructure), Array(), Array(), true, Array())

  private[this] def makeCompanions(name: String): Companions =
    new Companions(makeClassLike(name, DefinitionType.ClassDef), makeClassLike(name, DefinitionType.Module))

  private[this] def lzy[T <: AnyRef](x: T) = SafeLazyProxy.strict(x)

  def genNameHash(defn: String): Gen[xsbti.api.NameHash] =
    const(new xsbti.api.NameHash(defn, defn.hashCode()))

  def genNameHashes(defns: Seq[String]): Gen[xsbti.api.NameHashes] = {
    def partitionAccordingToMask[T](mask: List[Boolean], xs: List[T]): (List[T], List[T]) = {
      val (p1, p2) = (mask zip xs).partition(_._1)
      (p1.map(_._2), p2.map(_._2))
    }
    val genNameHashesList = Gen.sequence[List[NameHash], xsbti.api.NameHash](defns.map(genNameHash))
    val genTwoListOfNameHashes = for {
      nameHashesList <- genNameHashesList
      isRegularMemberList <- listOfN(nameHashesList.length, arbitrary[Boolean])
    } yield partitionAccordingToMask(isRegularMemberList, nameHashesList)
    for {
      (regularMemberNameHashes, implicitMemberNameHashes) <- genTwoListOfNameHashes
    } yield new xsbti.api.NameHashes(regularMemberNameHashes.toArray, implicitMemberNameHashes.toArray)
  }

  def genClass(name: String): Gen[AnalyzedClass] = for {
    startTime <- arbitrary[Long]
    apiHash <- arbitrary[Int]
    hasMacro <- arbitrary[Boolean]
    nameHashes <- genNameHashes(Seq(name))
  } yield new AnalyzedClass(startTime, name, SafeLazyProxy(makeCompanions(name)), apiHash, nameHashes, hasMacro)

  def genClasses(all_defns: Seq[String]): Gen[Seq[AnalyzedClass]] =
    Gen.sequence[List[AnalyzedClass], AnalyzedClass](all_defns.map(genClass))

  def genAPIs(rel: Relations): Gen[APIs] = {
    val internal = rel.internalClassDep._1s.toList.sorted ++ rel.internalClassDep._2s.toList.sorted
    val external = rel.allExternalDeps.toList.sorted
    for {
      internalSources <- genClasses(internal)
      externalSources <- genClasses(external)
    } yield APIs(zipMap(internal, internalSources), zipMap(external, externalSources))
  }

  def genRelation[T](g: Gen[T])(srcs: List[File]): Gen[Relation[File, T]] = for {
    n <- choose(1, maxRelatives)
    entries <- listOfN(srcs.length, containerOfN[Set, T](n, g))
  } yield Relation.reconstruct(zipMap(srcs, entries))

  val genFileRelation = genRelation[File](unique(genFile)) _
  val genStringRelation = genRelation[String](unique(identifier)) _

  def genStringStringRelation(num: Int): Gen[Relation[String, String]] = for {
    n <- choose(1, if (num == 0) 1 else num)
    fwd <- listOfN(n, unique(identifier))
    prv <- listOfN(n, unique(identifier))
  } yield Relation.reconstruct(zipMap(fwd, prv).mapValues(x => Set(x)))

  def genRClassDependencies(classNames: List[String]): Gen[Relations.ClassDependencies] = for {
    internal <- listOfN(classNames.length, someOf(classNames))
    external <- listOfN(classNames.length, someOf(classNames))
  } yield {
    def toForwardMap(targets: Seq[Seq[String]]): Map[String, Set[String]] =
      (classNames zip (targets map { _.toSet }) map { case (a, b) => (a, b - a) }).toMap
    Relations.makeClassDependencies(
      Relation.reconstruct(toForwardMap(internal)),
      Relation.reconstruct(toForwardMap(external))
    )
  }

  def genSubRClassDependencies(src: Relations.ClassDependencies): Gen[Relations.ClassDependencies] = for {
    internal <- someOf(src.internal.all.toList)
    external <- someOf(src.external.all.toList)
  } yield Relations.makeClassDependencies(Relation.empty ++ internal, Relation.empty ++ external)

  def genUsedNames(classNames: Seq[String]): Gen[Relation[String, String]] = for {
    allNames <- listOfN(classNames.length, containerOf[Set, String](identifier))
  } yield Relation.reconstruct(zipMap(classNames, allNames))

  def genRelationsNameHashing: Gen[Relations] = for {
    numSrcs <- choose(0, maxSources)
    srcs <- listOfN(numSrcs, genFile)
    productClassName <- genStringStringRelation(numSrcs)
    libraryClassName <- genStringRelation(srcs)
    srcProd <- genFileRelation(srcs)
    libraryDep <- genFileRelation(srcs)
    classNames = productClassName._1s.toList
    memberRef <- genRClassDependencies(classNames)
    inheritance <- genSubRClassDependencies(memberRef)
    localInheritance <- genSubRClassDependencies(memberRef)
    classes = Relation.reconstruct(zipMap(srcs, classNames).mapValues(x => Set(x)))
    names <- genUsedNames(classNames)
    internal <- InternalDependencies(Map(
      DependencyByMemberRef -> memberRef.internal,
      DependencyByInheritance -> inheritance.internal,
      LocalDependencyByInheritance -> localInheritance.internal
    ))
    external <- ExternalDependencies(Map(
      DependencyByMemberRef -> memberRef.external,
      DependencyByInheritance -> inheritance.external,
      LocalDependencyByInheritance -> localInheritance.external
    ))
  } yield Relations.make(srcProd, libraryDep, libraryClassName, internal, external, classes, names, productClassName)

  def genAnalysis: Gen[Analysis] = for {
    rels <- genRelationsNameHashing
    stamps <- genStamps(rels)
    apis <- genAPIs(rels)
  } yield new MAnalysis(stamps, apis, rels, SourceInfos.empty, Compilations.empty)
}
