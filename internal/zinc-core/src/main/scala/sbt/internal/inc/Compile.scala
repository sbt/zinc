/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import sbt.internal.inc.Analysis.{ LocalProduct, NonLocalProduct }
import xsbt.api.{ APIUtil, HashAPI, NameHashing }
import xsbti.api._
import xsbti.compile.{ CompileAnalysis, DependencyChanges, IncOptions, MultipleOutput, Output, SingleOutput }
import xsbti.{ Position, Problem, Severity }
import sbt.util.Logger
import sbt.util.Logger.{ m2o, problem }
import java.io.File
import xsbti.api.DependencyContext
import xsbti.api.DependencyContext.{ DependencyByInheritance, DependencyByMemberRef }
import xsbti.compile.ClassFileManager

/**
 * Helper methods for running incremental compilation.  All this is responsible for is
 * adapting any xsbti.AnalysisCallback into one compatible with the [[sbt.internal.inc.Incremental]] class.
 */
object IncrementalCompile {
  /**
   * Runs the incremental compilation algorithm.
   *
   * @param sources
   *              The full set of input sources
   * @param lookup
   *              An instance of the `Lookup` that implements looking up both classpath elements
   *              and Analysis object instances by a binary class name.
   * @param compile
   *                The mechanism to run a single 'step' of compile, for ALL source files involved.
   * @param previous0
   *                 The previous dependency Analysis (or an empty one).
   * @param output
   *               The configured output directory/directory mapping for source files.
   * @param log
   *            Where all log messages should go
   * @param options
   *                Incremental compiler options (like name hashing vs. not).
   * @return
   *         A flag of whether or not compilation completed succesfully, and the resulting dependency analysis object.
   *
   */
  def apply(sources: Set[File], lookup: Lookup,
    compile: (Set[File], DependencyChanges, xsbti.AnalysisCallback, ClassFileManager) => Unit,
    previous0: CompileAnalysis,
    output: Output, log: Logger,
    options: IncOptions): (Boolean, Analysis) =
    {
      val previous = previous0 match { case a: Analysis => a }
      val current = Stamps.initial(Stamp.lastModified, Stamp.hash, Stamp.lastModified)
      val internalBinaryToSourceClassName = (binaryClassName: String) =>
        previous.relations.productClassName.reverse(binaryClassName).headOption
      val internalSourceToClassNamesMap: File => Set[String] = (f: File) => previous.relations.classNames(f)
      val externalAPI = getExternalAPI(lookup)
      try {
        Incremental.compile(sources, lookup, previous, current, compile,
          new AnalysisCallback.Builder(internalBinaryToSourceClassName, internalSourceToClassNamesMap, externalAPI, current, output, options),
          log, options)
      } catch {
        case e: xsbti.CompileCancelled =>
          log.info("Compilation has been cancelled")
          // in case compilation got cancelled potential partial compilation results (e.g. produced classs files) got rolled back
          // and we can report back as there was no change (false) and return a previous Analysis which is still up-to-date
          (false, previous)
      }
    }
  def getExternalAPI(lookup: Lookup): (File, String) => Option[AnalyzedClass] =
    (file: File, binaryClassName: String) =>
      lookup.lookupAnalysis(binaryClassName) flatMap {
        case (analysis: Analysis) =>
          val sourceClassName = analysis.relations.productClassName.reverse(binaryClassName).headOption
          sourceClassName flatMap analysis.apis.internal.get
      }
}

private object AnalysisCallback {
  /** Allow creating new callback instance to be used in each compile iteration */
  class Builder(
    internalBinaryToSourceClassName: String => Option[String],
    internalSourceToClassNamesMap: File => Set[String],
    externalAPI: (File, String) => Option[AnalyzedClass], current: ReadStamps,
    output: Output, options: IncOptions
  ) {
    def build(): AnalysisCallback = new AnalysisCallback(
      internalBinaryToSourceClassName,
      internalSourceToClassNamesMap, externalAPI, current, output, options
    )
  }
}
private final class AnalysisCallback(
  internalBinaryToSourceClassName: String => Option[String],
  internalSourceToClassNamesMap: File => Set[String],
  externalAPI: (File, String) => Option[AnalyzedClass], current: ReadStamps,
  output: Output, options: IncOptions
) extends xsbti.AnalysisCallback {

  val compilation = {
    val outputSettings = output match {
      case single: SingleOutput => Array(new OutputSetting("/", single.outputDirectory.getAbsolutePath))
      case multi: MultipleOutput =>
        multi.outputGroups.map(out => new OutputSetting(out.sourceDirectory.getAbsolutePath, out.outputDirectory.getAbsolutePath))
    }
    new Compilation(System.currentTimeMillis, outputSettings)
  }

  override def toString =
    (List("Class APIs", "Object APIs", "Binary deps", "Products", "Source deps") zip
      List(classApis, objectApis, binaryDeps, nonLocalClasses, intSrcDeps)).
      map { case (label, map) => label + "\n\t" + map.mkString("\n\t") }.mkString("\n")

  import collection.mutable.{ HashMap, HashSet, ListBuffer, Map, Set }

  private[this] val srcs = Set[File]()
  private[this] val classApis = new HashMap[String, (HashAPI.Hash, ClassLike)]
  private[this] val objectApis = new HashMap[String, (HashAPI.Hash, ClassLike)]
  private[this] val classPublicNameHashes = new HashMap[String, NameHashes]
  private[this] val objectPublicNameHashes = new HashMap[String, NameHashes]
  private[this] val usedNames = new HashMap[String, Set[String]]
  private[this] val unreporteds = new HashMap[File, ListBuffer[Problem]]
  private[this] val reporteds = new HashMap[File, ListBuffer[Problem]]
  private[this] val binaryDeps = new HashMap[File, Set[File]]
  // source file to set of generated (class file, binary class name); only non local classes are stored here
  private[this] val nonLocalClasses = new HashMap[File, Set[(File, String)]]
  private[this] val localClasses = new HashMap[File, Set[File]]
  // mapping between src class name and binary (flat) class name for classes generated from src file
  private[this] val classNames = new HashMap[File, Set[(String, String)]]
  // generated class file to its source class name
  private[this] val classToSource = new HashMap[File, String]
  // internal source dependencies
  private[this] val intSrcDeps = new HashMap[String, Set[InternalDependency]]
  // external source dependencies
  private[this] val extSrcDeps = new HashMap[String, Set[ExternalDependency]]
  private[this] val binaryClassName = new HashMap[File, String]
  // source files containing a macro def.
  private[this] val macroClasses = Set[String]()

  private def add[A, B](map: Map[A, Set[B]], a: A, b: B): Unit = {
    map.getOrElseUpdate(a, new HashSet[B]) += b
    ()
  }

  def startSource(source: File): Unit = {
    assert(!srcs.contains(source), s"The startSource can be called only once per source file: $source")
    srcs += source
    ()
  }

  def problem(category: String, pos: Position, msg: String, severity: Severity, reported: Boolean): Unit =
    {
      for (source <- m2o(pos.sourceFile)) {
        val map = if (reported) reporteds else unreporteds
        map.getOrElseUpdate(source, ListBuffer.empty) += Logger.problem(category, pos, msg, severity)
      }
    }

  def classDependency(onClassName: String, sourceClassName: String, context: DependencyContext) = {
    if (onClassName != sourceClassName)
      add(intSrcDeps, sourceClassName, new InternalDependency(sourceClassName, onClassName, context))
  }

  private[this] def externalBinaryDependency(binary: File, className: String, source: File, context: DependencyContext): Unit = {
    binaryClassName.put(binary, className)
    add(binaryDeps, source, binary)
  }

  private[this] def externalSourceDependency(sourceClassName: String, targetBinaryClassName: String, targetClass: AnalyzedClass, context: DependencyContext): Unit = {
    val dependency = new ExternalDependency(sourceClassName, targetBinaryClassName, targetClass, context)
    add(extSrcDeps, sourceClassName, dependency)
  }

  def binaryDependency(classFile: File, onBinaryClassName: String, fromClassName: String, fromSourceFile: File, context: DependencyContext) =
    internalBinaryToSourceClassName(onBinaryClassName) match {
      case Some(dependsOn) => // dependsOn is a source class name
        // dependency is a product of a source not included in this compilation
        classDependency(dependsOn, fromClassName, context)
      case None =>
        classToSource.get(classFile) match {
          case Some(dependsOn) =>
            // dependency is a product of a source in this compilation step,
            //  but not in the same compiler run (as in javac v. scalac)
            classDependency(dependsOn, fromClassName, context)
          case None =>
            externalDependency(classFile, onBinaryClassName, fromClassName, fromSourceFile, context)
        }
    }

  private[this] def externalDependency(classFile: File, onBinaryName: String, sourceClassName: String,
    sourceFile: File, context: DependencyContext): Unit =
    externalAPI(classFile, onBinaryName) match {
      case Some(api) =>
        // dependency is a product of a source in another project
        val targetBinaryClassName = onBinaryName
        externalSourceDependency(sourceClassName, targetBinaryClassName, api, context)
      case None =>
        // dependency is some other binary on the classpath
        externalBinaryDependency(classFile, onBinaryName, sourceFile, context)
    }

  def generatedNonLocalClass(source: File, classFile: File, binaryClassName: String, srcClassName: String): Unit = {
    add(nonLocalClasses, source, (classFile, binaryClassName))
    add(classNames, source, (srcClassName, binaryClassName))
    classToSource.put(classFile, srcClassName)
    ()
  }

  def generatedLocalClass(source: File, classFile: File): Unit = {
    add(localClasses, source, classFile)
    ()
  }

  // empty value used when name hashing algorithm is disabled
  private val emptyNameHashes = new xsbti.api.NameHashes(Array.empty, Array.empty)

  def api(sourceFile: File, classApi: ClassLike): Unit = {
    import xsbt.api.{ APIUtil, HashAPI }
    val className = classApi.name
    if (APIUtil.isScalaSourceName(sourceFile.getName) && APIUtil.hasMacro(classApi)) macroClasses += className
    val shouldMinimize = !Incremental.apiDebug(options)
    val savedClassApi = if (shouldMinimize) APIUtil.minimize(classApi) else classApi
    val apiHash: HashAPI.Hash = HashAPI(classApi)
    val nameHashes = (new xsbt.api.NameHashing).nameHashes(classApi)
    classApi.definitionType match {
      case DefinitionType.ClassDef | DefinitionType.Trait =>
        classApis(className) = apiHash -> savedClassApi
        classPublicNameHashes(className) = nameHashes
      case DefinitionType.Module | DefinitionType.PackageModule =>
        objectApis(className) = apiHash -> savedClassApi
        objectPublicNameHashes(className) = nameHashes
    }
  }

  def usedName(className: String, name: String) = add(usedNames, className, name)

  def nameHashing: Boolean = options.nameHashing

  override def enabled(): Boolean = options.enabled

  def get: Analysis =
    addUsedNames(addCompilation(addProductsAndDeps(Analysis.empty(nameHashing = nameHashing))))

  def getOrNil[A, B](m: collection.Map[A, Seq[B]], a: A): Seq[B] = m.get(a).toList.flatten
  def addCompilation(base: Analysis): Analysis = base.copy(compilations = base.compilations.add(compilation))
  def addUsedNames(base: Analysis): Analysis = (base /: usedNames) {
    case (a, (className, names)) =>
      (a /: names) { case (a, name) => a.copy(relations = a.relations.addUsedName(className, name)) }
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

  private def analyzeClass(name: String): AnalyzedClass = {
    val hasMacro: Boolean = macroClasses.contains(name)
    val (companions, apiHash) = companionsWithHash(name)
    val nameHashes = nameHashesForCompanions(name)
    val safeCompanions = SafeLazyProxy(companions)
    val ac = new AnalyzedClass(compilation.startTime(), name, safeCompanions, apiHash, nameHashes, hasMacro)
    ac
  }

  def addProductsAndDeps(base: Analysis): Analysis =
    (base /: srcs) {
      case (a, src) =>
        val stamp = current.internalSource(src)
        val classesInSrc = classNames.getOrElse(src, Set.empty).map(_._1)
        val analyzedApis = classesInSrc.map(analyzeClass)
        val info = SourceInfos.makeInfo(getOrNil(reporteds, src), getOrNil(unreporteds, src))
        val binaries = binaryDeps.getOrElse(src, Nil: Iterable[File])
        val localProds = localClasses.getOrElse(src, Nil: Iterable[File]) map {
          classFile => LocalProduct(classFile, current product classFile)
        }
        val binaryToSrcClassName = (classNames.getOrElse(src, Set.empty) map {
          case (srcClassName, binaryClassName) => (binaryClassName, srcClassName)
        }).toMap
        val nonLocalProds = nonLocalClasses.getOrElse(src, Nil: Iterable[(File, String)]) map {
          case (classFile, binaryClassName) =>
            val srcClassName = binaryToSrcClassName(binaryClassName)
            NonLocalProduct(srcClassName, binaryClassName, classFile, current product classFile)
        }

        val internalDeps = classesInSrc.flatMap(cls => intSrcDeps.getOrElse(cls, Set.empty))
        val externalDeps = classesInSrc.flatMap(cls => extSrcDeps.getOrElse(cls, Set.empty))
        val binDeps = binaries.map(d => (d, binaryClassName(d), current binary d))

        a.addSource(src, analyzedApis, stamp, info, nonLocalProds, localProds, internalDeps, externalDeps, binDeps)
    }

  override def dependencyPhaseCompleted(): Unit = {}

  override def apiPhaseCompleted(): Unit = {}
}
