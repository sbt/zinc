package sbt
package internal
package inc

import java.io.File

import scala.collection.mutable.{ ArrayBuffer, HashMap }
import xsbti.api._
import xsbti.api.DependencyContext._
import xsbt.api.{ HashAPI, NameHashing, APIUtil }
import sbt.internal.util.Relation

case class TestAnalysis(
  relations: inc.Relations,
  classDependencies: Set[(String, String, DependencyContext)],
  binaryDependencies: Set[(File, String, String, DependencyContext)],
  products: Set[(File, File)],
  binaryClassNames: Set[(String, String)],
  usedNames: Map[String, Set[String]],
  apis: APIs
) {

  def merge(o: TestAnalysis, deletedFiles: Seq[File]): TestAnalysis = {
    val deletedClasses = deletedFiles.flatMap(o.relations.classNames).toSet
    TestAnalysis(
      o.relations ++ relations -- deletedFiles,
      o.classDependencies ++ classDependencies filterNot (f => deletedClasses contains f._2),
      o.binaryDependencies ++ binaryDependencies filterNot (f => deletedClasses contains f._3),
      o.products ++ products filterNot (f => deletedFiles contains f._1),
      o.binaryClassNames ++ binaryClassNames filterNot (bc => deletedClasses contains bc._1),
      o.usedNames ++ usedNames filterKeys (k => !(deletedClasses contains k)),
      o.apis ++ apis removeInternal deletedClasses
    )
  }
}
object TestAnalysis {
  val Empty = TestAnalysis(Relations.empty, Set.empty, Set.empty, Set.empty, Set.empty, Map.empty, APIs.empty)
}

class TestAnalysisCallback(
  internalBinaryToSourceClassName: Map[String, String],
  override val nameHashing: Boolean = false
) extends xsbti.AnalysisCallback {
  val classDependencies = new ArrayBuffer[(String, String, DependencyContext)]
  val binaryDependencies = new ArrayBuffer[(File, String, String, DependencyContext)]
  val products = new ArrayBuffer[(File, File)]
  val usedNames = scala.collection.mutable.Map.empty[String, Set[String]].withDefaultValue(Set.empty)
  val classNames = scala.collection.mutable.Map.empty[File, Set[(String, String)]].withDefaultValue(Set.empty)
  val macroClasses = scala.collection.mutable.Set[String]()
  val classApis = new HashMap[String, (HashAPI.Hash, ClassLike)]
  val objectApis = new HashMap[String, (HashAPI.Hash, ClassLike)]
  val classPublicNameHashes = new HashMap[String, NameHashes]
  val objectPublicNameHashes = new HashMap[String, NameHashes]

  private val compilation = new Compilation(System.currentTimeMillis, Array.empty)

  def hashFile(f: File): Array[Byte] = Stamp.hash(f).asInstanceOf[Hash].value

  def get: TestAnalysis = {

    val p = (products foldLeft Relation.empty[File, File]) {
      case (rel, (source, module)) => rel + (source -> module)
    }

    val bin = (binaryDependencies foldLeft Relation.empty[String, File]) {
      case (rel, (binary, _, sourceClassName, _)) => rel + (sourceClassName -> binary)
    }

    val di = Relation.empty[File, File]
    val de = Relation.empty[File, String]

    val pii = Relation.empty[File, File]
    val pie = Relation.empty[File, String]

    val mri = (classDependencies.filter(_._3 == DependencyByMemberRef) foldLeft Relation.empty[String, String]) {
      case (rel, (dependsOnClassName, sourceClassName, _)) => rel + (sourceClassName -> dependsOnClassName)
    }
    val mre = Relation.empty[File, String]

    val ii = (classDependencies.filter(_._3 == DependencyByInheritance) foldLeft Relation.empty[String, String]) {
      case (rel, (dependsOnClassName, sourceClassName, _)) => rel + (sourceClassName -> dependsOnClassName)
    }
    val ie = Relation.empty[File, String]

    val cn = Relation.empty[File, String]

    val bcn = Relation.empty[String, String] ++ classNames.values.flatten

    val un = (usedNames foldLeft Relation.empty[String, String]) {
      case (rel, (sourceClassName, names)) => rel ++ (names map (n => (sourceClassName, n)))
    }

    val relations = Relations.construct(true, p :: bin :: di :: de :: pii :: pie :: mri :: mre :: ii :: ie :: cn :: un :: bcn :: Nil)

    val analyzedApis = classNames.values.flatMap(_.map(_._1)).map(analyzeClass)

    val apisByClassName = analyzedApis.map(a => a.name -> a)

    TestAnalysis(relations, classDependencies.toSet, binaryDependencies.toSet, products.toSet,
      classNames.values.flatten.toSet, usedNames.toMap, APIs(apisByClassName.toMap, Map.empty))
  }

  private def analyzeClass(name: String): AnalyzedClass = {
    val hasMacro: Boolean = macroClasses.contains(name)
    val (companions, apiHash) = companionsWithHash(name)
    val nameHashes = nameHashesForCompanions(name)
    val ac = new AnalyzedClass(compilation, name, SafeLazyProxy(companions), apiHash, nameHashes, hasMacro)
    ac
  }

  private def companionsWithHash(className: String): (Companions, HashAPI.Hash) = {
    val emptyHash = -1
    lazy val emptyClass = emptyHash -> APIUtil.emptyClassLike(className, DefinitionType.ClassDef)
    lazy val emptyObject = emptyHash -> APIUtil.emptyClassLike(className, DefinitionType.Module)
    val (classApiHash, classApi) = classApis.getOrElse(className, emptyClass)
    val (objectApiHash, objectApi) = objectApis.getOrElse(className, emptyObject)
    val companions = new Companions(classApi, objectApi)
    val apiHash = (classApiHash, objectApiHash).hashCode
    (companions, apiHash)
  }

  private def nameHashesForCompanions(className: String): NameHashes = {
    val classNameHashes = classPublicNameHashes.get(className)
    val objectNameHashes = objectPublicNameHashes.get(className)
    (classNameHashes, objectNameHashes) match {
      case (Some(nm1), Some(nm2)) =>
        NameHashing.merge(nm1, nm2)
      case (Some(nm), None) => nm
      case (None, Some(nm)) => nm
      case (None, None)     => sys.error("Failed to find name hashes for " + className)
    }
  }

  def startSource(source: File): Unit = {
  }

  def classDependency(onClassName: String, sourceClassName: String, context: DependencyContext): Unit = {
    if (onClassName != sourceClassName)
      classDependencies += ((onClassName, sourceClassName, context))
    ()
  }

  def binaryDependency(onBinary: File, onBinaryClassName: String, fromClassName: String, fromSourceFile: File, context: DependencyContext): Unit = {
    internalBinaryToSourceClassName get onBinaryClassName match {
      case Some(internal) => classDependency(internal, fromClassName, context)
      case None           => binaryDependencies += ((onBinary, onBinaryClassName, fromClassName, context)); ()
    }
  }

  def generatedNonLocalClass(source: File, module: File, binaryClassName: String, srcClassName: String): Unit = {
    products += ((source, module))
    classNames(source) += ((srcClassName, binaryClassName))
  }

  def generatedLocalClass(source: File, module: File): Unit = {
    products += ((source, module))
    ()
  }

  def usedName(className: String, name: String): Unit = { usedNames(className) += name }

  def api(source: File, api: ClassLike): Unit = {
    val className = api.name
    if (APIUtil.hasMacro(api)) macroClasses += className
    val apiHash: HashAPI.Hash = HashAPI(api)
    val nameHashes = (new xsbt.api.NameHashing).nameHashes(api)
    api.definitionType match {
      case DefinitionType.ClassDef | DefinitionType.Trait =>
        classApis(className) = apiHash -> api
        classPublicNameHashes(className) = nameHashes
      case DefinitionType.Module | DefinitionType.PackageModule =>
        objectApis(className) = apiHash -> api
        objectPublicNameHashes(className) = nameHashes
    }
  }

  def problem(category: String, pos: xsbti.Position, message: String, severity: xsbti.Severity, reported: Boolean): Unit = ()

  override def dependencyPhaseCompleted(): Unit = {}

  override def apiPhaseCompleted(): Unit = {}
}
