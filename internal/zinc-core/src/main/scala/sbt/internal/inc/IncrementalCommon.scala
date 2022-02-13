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

import sbt.util.Logger
import xsbti.{ FileConverter, VirtualFile, VirtualFileRef }
import xsbt.api.APIUtil
import xsbti.api.AnalyzedClass
import xsbti.compile.{ Changes, DependencyChanges, IncOptions, Output }
import xsbti.compile.{ ClassFileManager => XClassFileManager }
import xsbti.compile.analysis.{ ReadStamps, Stamp => XStamp }
import scala.collection.Iterator
import scala.collection.parallel.immutable.ParVector
import Incremental.{ CompileCycle, CompileCycleResult, IncrementalCallback, PrefixingLogger }

/**
 * Defines the core logic to compile incrementally and apply the class invalidation after
 * every compiler run. This class defines only the core logic and the algorithm-specific
 * bits are implemented in its subclasses.
 *
 * In the past, there were several incremental compiler strategies. Now, there's only
 * one, the default [[IncrementalNameHashing]] strategy that invalidates classes based
 * on name hashes.
 *
 * @param log An instance of a logger.
 * @param options An instance of incremental compiler options.
 */
private[inc] abstract class IncrementalCommon(
    val log: Logger,
    options: IncOptions,
    profiler: RunProfiler
) extends InvalidationProfilerUtils {
  // Work around bugs in classpath handling such as the "currently" problematic -javabootclasspath
  private[this] def enableShallowLookup: Boolean =
    java.lang.Boolean.getBoolean("xsbt.skip.cp.lookup")

  private[this] final val wrappedLog = new PrefixingLogger("[inv] ")(log)
  def debug(s: => String): Unit = if (options.relationsDebug) wrappedLog.debug(s) else ()

  final def iterations(state0: CycleState): Iterator[CycleState] =
    new Iterator[CycleState] {
      var state: CycleState = state0
      override def hasNext: Boolean = state.hasNext
      override def next(): CycleState = {
        val n = state.next
        state = n
        n
      }
    }
  case class CycleState(
      invalidatedClasses: Set[String],
      initialChangedSources: Set[VirtualFileRef],
      allSources: Set[VirtualFile],
      converter: FileConverter,
      binaryChanges: DependencyChanges,
      lookup: ExternalLookup,
      previous: Analysis,
      doCompile: CompileCycle,
      classfileManager: XClassFileManager,
      output: Output,
      cycleNum: Int,
  ) {
    def toVf(ref: VirtualFileRef): VirtualFile = converter.toVirtualFile(ref)
    def sourceRefs: Set[VirtualFileRef] = allSources.asInstanceOf[Set[VirtualFileRef]]
    lazy val javaSources: Set[VirtualFileRef] = sourceRefs.filter(_.id.endsWith(".java"))

    def hasNext: Boolean = invalidatedClasses.nonEmpty || initialChangedSources.nonEmpty

    def next: CycleState = {
      // Compute all the invalidated classes by aggregating invalidated package objects
      val invalidatedByPackageObjects =
        invalidatedPackageObjects(invalidatedClasses, previous.relations, previous.apis)
      val classesToRecompile = invalidatedClasses ++ invalidatedByPackageObjects

      // Computes which source files are mapped to the invalidated classes and recompile them
      val invalidatedRefs: Set[VirtualFileRef] =
        mapInvalidationsToSources(classesToRecompile, initialChangedSources, sourceRefs, previous)

      val invalidatedSources: Set[VirtualFile] = invalidatedRefs.map(toVf)

      val pruned = IncrementalCommon
        .pruneClassFilesOfInvalidations(invalidatedSources, previous, classfileManager, converter)

      debug(s"********* Pruned: \n${pruned.relations}\n*********")

      val handler = new IncrementalCallbackImpl(
        invalidatedSources,
        classfileManager,
        pruned,
        classesToRecompile,
        profiler.registerCycle(
          invalidatedClasses,
          invalidatedByPackageObjects,
          initialChangedSources,
          invalidatedSources,
          _,
          _,
          _,
          _
        )
      )

      // Actual compilation takes place here
      log.debug(s"compilation cycle $cycleNum")
      val result = doCompile.run(invalidatedSources, binaryChanges, handler)
      val CompileCycleResult(continue, nextInvalidations, current) = result

      // Include all Java sources into each Scala compilation cycle during pipelining.
      // In normal compilation without pipelining the product of javac compilation would
      // be available in /classes directory, but with pipelining javac compilation is
      // deferred till later.
      // This means that for each cycle (A.scala), (B.scala, ...), etc all Java sources
      // must be included into scalac invocation so the sources that are being recompiled
      // get to see the symbols coming from Java.
      // See also sbt/zinc#918
      val nextChangedSources: Set[VirtualFileRef] =
        if (continue && !handler.isFullCompilation && options.pipelining) javaSources
        else Set.empty

      // Return immediate analysis as all sources have been recompiled
      copy(
        if (continue && !handler.isFullCompilation) nextInvalidations else Set.empty,
        nextChangedSources,
        binaryChanges = IncrementalCommon.emptyChanges,
        previous = current,
        cycleNum = cycleNum + 1,
      )
    }

    /**
     * IncrementalCallbackImpl is a callback hanlder that the custom
     * phases injected by Zinc call back to perform certain operations mid-compilation.
     * In particular, for pipelining, we need to know whether the current
     * incremental cycle is going to be the last cycle or not.
     */
    class IncrementalCallbackImpl(
        invalidatedSources: Set[VirtualFile],
        classFileManager: XClassFileManager,
        pruned: Analysis,
        classesToRecompile: Set[String],
        registerCycle: (Set[String], APIChanges, Set[String], Boolean) => Unit
    ) extends IncrementalCallback(classFileManager) {
      override val isFullCompilation: Boolean = allSources.subsetOf(invalidatedSources)
      override val previousAnalysisPruned: Analysis = pruned

      override def mergeAndInvalidate(
          partialAnalysis: Analysis,
          completingCycle: Boolean
      ): CompileCycleResult = {
        val analysis =
          if (isFullCompilation)
            partialAnalysis.copy(compilations = pruned.compilations ++ partialAnalysis.compilations)
          else pruned ++ partialAnalysis

        // Represents classes detected as changed externally and internally (by a previous cycle)
        // Maps the changed sources by the user to class names we can count as invalidated
        val getClasses = (a: Analysis) => initialChangedSources.flatMap(a.relations.classNames)
        val recompiledClasses = classesToRecompile ++ getClasses(previous) ++ getClasses(analysis)

        val newApiChanges =
          detectAPIChanges(recompiledClasses, previous.apis.internalAPI, analysis.apis.internalAPI)
        debug(s"\nChanges:\n$newApiChanges")

        val nextInvalidations =
          if (isFullCompilation) Set.empty[String]
          else
            invalidateAfterInternalCompilation(
              analysis.relations,
              newApiChanges,
              recompiledClasses,
              cycleNum >= options.transitiveStep,
              IncrementalCommon.comesFromScalaSource(previous.relations, Some(analysis.relations)) _
            )

        // No matter what shouldDoIncrementalCompilation returns, we are not in fact going to
        // continue if there are no invalidations.
        val continue = nextInvalidations.nonEmpty &&
          lookup.shouldDoIncrementalCompilation(nextInvalidations, analysis)

        val hasScala = Analysis.sources(partialAnalysis).scala.nonEmpty

        // If we're completing the cycle and we had scala sources, then mergeAndInvalidate has already been called
        if (!completingCycle || !hasScala) {
          registerCycle(recompiledClasses, newApiChanges, nextInvalidations, continue)
        }
        CompileCycleResult(continue, nextInvalidations, analysis)
      }

      override def completeCycle(
          prev: Option[CompileCycleResult],
          partialAnalysis: Analysis
      ): CompileCycleResult = {
        classFileManager.generated(partialAnalysis.relations.allProducts.map(toVf).toArray)
        prev match {
          case Some(prev) => prev.copy(analysis = pruned ++ partialAnalysis)
          case _          => mergeAndInvalidate(partialAnalysis, true)
        }
      }
    }
  }

  /**
   * Compile a project as many times as it is required incrementally. This logic is the start
   * point of the incremental compiler and the place where all the invalidation logic happens.
   *
   * The current logic does merge the compilation step and the analysis step, by making them
   * execute sequentially. There are cases where, for performance reasons, build tools and
   * users of Zinc may be interested in separating the two. If this is the case, the user needs
   * to reimplement this logic by copy pasting this logic and relying on the utils defined
   * in `IncrementalCommon`.
   *
   * @param invalidatedClasses The invalidated classes either initially or by a previous cycle.
   * @param initialChangedSources The initial changed sources by the user, empty if previous cycle.
   * @param allSources All the sources defined in the project and compiled in the first iteration.
   * @param converter FileConverter to convert between Path and VirtualFileRef.
   * @param binaryChanges The initially detected changes derived from [[InitialChanges]].
   * @param lookup The lookup instance to query classpath and analysis information.
   * @param previous The last analysis file known of this project.
   * @param doCompile A function that compiles a project and returns an analysis file.
   * @param classfileManager The manager that takes care of class files in compilation.
   * @param cycleNum The counter of incremental compiler cycles.
   * @return A fresh analysis file after all the incremental compiles have been run.
   */
  final def cycle(
      invalidatedClasses: Set[String],
      initialChangedSources: Set[VirtualFileRef],
      allSources: Set[VirtualFile],
      converter: FileConverter,
      binaryChanges: DependencyChanges,
      lookup: ExternalLookup,
      previous: Analysis,
      doCompile: CompileCycle,
      classfileManager: XClassFileManager,
      output: Output,
      cycleNum: Int,
  ): Analysis = {
    var s = CycleState(
      invalidatedClasses,
      initialChangedSources,
      allSources,
      converter,
      binaryChanges,
      lookup,
      previous,
      doCompile,
      classfileManager,
      output,
      cycleNum,
    )
    val it = iterations(s)
    while (it.hasNext) {
      s = it.next()
    }
    s.previous
  }

  def mapInvalidationsToSources(
      invalidatedClasses: Set[String],
      aggregateSources: Set[VirtualFileRef],
      allSources: Set[VirtualFileRef],
      previous: Analysis
  ): Set[VirtualFileRef] = {
    def expand(invalidated: Set[VirtualFileRef]): Set[VirtualFileRef] = {
      val recompileAllFraction = options.recompileAllFraction
      // when pipelining we currently always invalidate all java sources, so it doesn't make sense to include them
      // when checking recompileAllFraction
      def countRelevant(ss: Set[VirtualFileRef]): Int =
        if (options.pipelining) ss.count(_.name.endsWith(".scala")) else ss.size

      if (countRelevant(invalidated) <= countRelevant(allSources) * recompileAllFraction)
        invalidated
      else {
        log.debug(
          s"Recompiling all sources: number of invalidated sources > ${recompileAllFraction * 100.00}% of all sources"
        )
        allSources ++ invalidated // Union because `all` doesn't contain removed sources
      }
    }

    expand(invalidatedClasses.flatMap(previous.relations.definesClass) ++ aggregateSources)
  }

  /**
   * Detects the API changes of `recompiledClasses`.
   *
   * @param recompiledClasses The list of classes that were recompiled in this round.
   * @param oldAPI A function that returns the previous class associated with a given class name.
   * @param newAPI A function that returns the current class associated with a given class name.
   * @return A list of API changes of the given two analyzed classes.
   */
  def detectAPIChanges(
      recompiledClasses: collection.Set[String],
      oldAPI: String => AnalyzedClass,
      newAPI: String => AnalyzedClass
  ): APIChanges = {
    // log.debug(s"[zinc] detectAPIChanges(recompiledClasses = $recompiledClasses)")
    def classDiff(className: String, a: AnalyzedClass, b: AnalyzedClass): Option[APIChange] = {
      // log.debug(s"[zinc] classDiff($className, ${a.name}, ${b.name})")
      if (a.compilationTimestamp() == b.compilationTimestamp() && (a.apiHash == b.apiHash)) None
      else {
        val hasMacro = a.hasMacro || b.hasMacro
        if (hasMacro && IncOptions.getRecompileOnMacroDef(options)) {
          Some(APIChangeDueToMacroDefinition(className))
        } else findAPIChange(className, a, b)
      }
    }
    val apiChanges = recompiledClasses.flatMap(name => classDiff(name, oldAPI(name), newAPI(name)))
    if (Incremental.apiDebug(options) && apiChanges.nonEmpty) {
      logApiChanges(apiChanges, oldAPI, newAPI)
    }
    new APIChanges(apiChanges)
  }

  /**
   * Detects the initial changes after the first compiler iteration is over.
   *
   * This method only requires the compiled sources, the previous analysis and the
   * stamps reader to be able to populate [[InitialChanges]] with all the data
   * structures that will be used for the first incremental compiler cycle.
   *
   * The logic of this method takes care of the following tasks:
   *
   * 1. Detecting the sources that changed between the past and present compiler iteration.
   * 2. Detecting the removed products based on the stamps from the previous and current products.
   * 3. Detects the class names changed in a library (classpath entry such as jars or analysis).
   * 4. Computes the API changes in dependent and external projects.
   *
   * @param sources The sources that were compiled.
   * @param previousAnalysis The analysis from the previous compilation.
   * @param stamps The stamps reader to get stamp for sources, products and binaries.
   * @param lookup The lookup instance that provides hooks and inspects the classpath.
   * @param equivS A function to compare stamps.
   * @return An instance of [[InitialChanges]].
   */
  def detectInitialChanges(
      sources: Set[VirtualFile],
      previousAnalysis: Analysis,
      stamps: ReadStamps,
      lookup: Lookup,
      converter: FileConverter,
      output: Output
  )(implicit equivS: Equiv[XStamp]): InitialChanges = {
    import IncrementalCommon.isLibraryModified
    import lookup.lookupAnalyzedClass
    val previous = previousAnalysis.stamps
    val previousRelations = previousAnalysis.relations

    val sourceChanges: Changes[VirtualFileRef] = lookup.changedSources(previousAnalysis).getOrElse {
      val previousSources = previous.allSources

      log.debug(s"previous = $previous")
      log.debug(s"current source = $sources")

      new UnderlyingChanges[VirtualFileRef] {
        val sourceIds = sources.map(_.id)
        val previousSourceIds = previousSources.map(_.id)

        val added0 = java.util.concurrent.ConcurrentHashMap.newKeySet[VirtualFileRef]
        val changed0 = java.util.concurrent.ConcurrentHashMap.newKeySet[VirtualFileRef]
        val removed0 = java.util.concurrent.ConcurrentHashMap.newKeySet[VirtualFileRef]
        val unmodified0 = java.util.concurrent.ConcurrentHashMap.newKeySet[VirtualFileRef]

        new ParVector(sources.toVector).foreach { f =>
          if (previousSourceIds.contains(f.id)) {
            if (equivS.equiv(previous.source(f), stamps.source(f))) unmodified0.add(f)
            else changed0.add(f)
          } else added0.add(f)
        }
        previousSources.foreach(f => if (!sourceIds.contains(f.id)) removed0.add(f))

        val added = new WrappedSet(added0)
        val changed = new WrappedSet(changed0)
        val removed = new WrappedSet(removed0)
        val unmodified = new WrappedSet(unmodified0)
      }
    }

    val removedProducts: Set[VirtualFileRef] =
      lookup.removedProducts(previousAnalysis).getOrElse {
        new ParVector(previous.allProducts.toVector)
          .filter(p => {
            // println(s"removedProducts? $p")
            !equivS.equiv(previous.product(p), stamps.product(p))
          })
          .toVector
          .toSet
      }

    val changedLibraries: Set[VirtualFileRef] = lookup.changedBinaries(previousAnalysis).getOrElse {
      val detectChange =
        isLibraryModified(
          enableShallowLookup,
          lookup,
          previous,
          stamps,
          previousRelations,
          converter,
          log
        )
      new ParVector(previous.allLibraries.toVector).filter(detectChange).toVector.toSet
    }

    val subprojectApiChanges: APIChanges = {
      val incrementalExternalChanges = {
        val previousAPIs = previousAnalysis.apis
        val externalFinder = lookupAnalyzedClass(_: String, None).getOrElse(APIs.emptyAnalyzedClass)
        detectAPIChanges(previousAPIs.allExternals, previousAPIs.externalAPI, externalFinder)
      }

      val changedExternalClassNames = incrementalExternalChanges.allModified.toSet
      if (!lookup.shouldDoIncrementalCompilation(changedExternalClassNames, previousAnalysis))
        new APIChanges(Nil)
      else incrementalExternalChanges
    }

    val init =
      InitialChanges(sourceChanges, removedProducts, changedLibraries, subprojectApiChanges)
    profiler.registerInitial(init)
    // log.debug(s"initial changes: $init")
    init
  }

  /**
   * Invalidates classes internally to a project after an incremental compiler run.
   *
   * @param relations The relations produced by the immediate previous incremental compiler cycle.
   * @param changes The changes produced by the immediate previous incremental compiler cycle.
   * @param recompiledClasses The immediately recompiled class names.
   * @param invalidateTransitively A flag that tells whether transitive invalidations should be
   *                               applied. This flag is only enabled when there have been more
   *                               than `incOptions.transitiveStep` incremental runs.
   * @param isScalaClass A function to know if a class name comes from a Scala source file or not.
   * @return A list of invalidated class names for the next incremental compiler run.
   */
  def invalidateAfterInternalCompilation(
      relations: Relations,
      changes: APIChanges,
      recompiledClasses: Set[String],
      invalidateTransitively: Boolean,
      isScalaClass: String => Boolean
  ): Set[String] = {
    val initial = changes.allModified.toSet
    val dependsOnClass = findClassDependencies(_, relations)
    val firstClassInvalidation: Set[String] = {
      val invalidated = if (invalidateTransitively) {
        // Invalidate by brute force (normally happens when we've done more than 3 incremental runs)
        IncrementalCommon.transitiveDeps(initial, log)(dependsOnClass)
      } else {
        changes.apiChanges.flatMap(invalidateClassesInternally(relations, _, isScalaClass)).toSet
      }
      val included = includeTransitiveInitialInvalidations(initial, invalidated, dependsOnClass)
      log.debug("Final step, transitive dependencies:\n\t" + included)
      included
    }

    // Invalidate classes linked with a class file that is produced by more than one source file
    val secondClassInvalidation = IncrementalCommon.invalidateNamesProducingSameClassFile(relations)
    if (secondClassInvalidation.nonEmpty)
      log.debug(s"Invalidated due to generated class file collision: ${secondClassInvalidation}")

    val newInvalidations = (firstClassInvalidation -- recompiledClasses) ++ secondClassInvalidation
    if (newInvalidations.isEmpty) {
      log.debug("No classes were invalidated.")
      Set.empty
    } else {
      val allInvalidations = firstClassInvalidation ++ secondClassInvalidation
      log.debug(s"Invalidated classes: ${allInvalidations.mkString(", ")}")
      allInvalidations
    }
  }

  /** Invalidates classes and sources based on initially detected 'changes' to the sources, products, and dependencies. */
  def invalidateInitial(
      previous: Relations,
      changes: InitialChanges
  ): (Set[String], Set[VirtualFileRef]) = {
    def classNames(srcs: Set[VirtualFileRef]): Set[String] = srcs.flatMap(previous.classNames)
    def toImmutableSet(srcs: java.util.Set[VirtualFileRef]): Set[VirtualFileRef] = {
      import scala.collection.JavaConverters.asScalaIteratorConverter
      srcs.iterator().asScala.toSet
    }

    val srcChanges = changes.internalSrc
    val removedSrcs = toImmutableSet(srcChanges.getRemoved)
    val modifiedSrcs = toImmutableSet(srcChanges.getChanged)
    val addedSrcs = toImmutableSet(srcChanges.getAdded)
    IncrementalCommon.checkAbsolute(addedSrcs)

    val removedClasses = classNames(removedSrcs)
    val dependentOnRemovedClasses = removedClasses.flatMap(previous.memberRef.internal.reverse)
    val modifiedClasses = classNames(modifiedSrcs)
    val invalidatedClasses = removedClasses ++ dependentOnRemovedClasses ++ modifiedClasses

    val byProduct = changes.removedProducts.flatMap(previous.produced)
    val byLibraryDep = changes.libraryDeps.flatMap(previous.usesLibrary)
    val byExtSrcDep = {
      // Invalidate changes
      val isScalaSource = IncrementalCommon.comesFromScalaSource(previous) _
      changes.external.apiChanges.iterator.flatMap { externalAPIChange =>
        invalidateClassesExternally(previous, externalAPIChange, isScalaSource)
      }.toSet
    }

    val allInvalidatedClasses = invalidatedClasses ++ byExtSrcDep
    val allInvalidatedSourcefiles = addedSrcs ++ modifiedSrcs ++ byProduct ++ byLibraryDep

    if (previous.allSources.isEmpty)
      log.debug("Full compilation, no sources in previous analysis.")
    else if (allInvalidatedClasses.isEmpty && allInvalidatedSourcefiles.isEmpty)
      log.debug("No changes")
    else
      log.debug(s"""
        |Initial source changes:
        |	removed: $removedSrcs
        |	added: $addedSrcs
        |	modified: $modifiedSrcs
        |Invalidated products: ${changes.removedProducts}
        |External API changes: ${changes.external}
        |Modified binary dependencies: ${changes.libraryDeps}
        |Initial directly invalidated classes: $invalidatedClasses
        |Sources indirectly invalidated by:
        |	product: $byProduct
        |	binary dep: $byLibraryDep
        |	external source: $byExtSrcDep""".stripMargin)

    (allInvalidatedClasses, allInvalidatedSourcefiles)
  }

  /**
   * Returns the invalidations that are the result of the `currentInvalidations` + the
   * `previousInvalidations` that depend transitively on `currentInvalidations`.
   *
   * We do this step on every incremental compiler iteration of a project where
   * `previousInvalidations` typically refers to the classes invalidated in the
   * previous incremental compiler cycle.
   *
   * @param previousInvalidations
   * @param currentInvalidations
   * @param findClassDependencies
   * @return
   */
  private[this] def includeTransitiveInitialInvalidations(
      previousInvalidations: Set[String],
      currentInvalidations: Set[String],
      findClassDependencies: String => Set[String]
  ): Set[String] = {
    val newInvalidations = currentInvalidations -- previousInvalidations
    log.debug(s"New invalidations:${ppxs(newInvalidations)}")

    val newTransitiveInvalidations =
      IncrementalCommon.transitiveDeps(newInvalidations, log)(findClassDependencies)
    // Include the initial invalidations that are present in the transitive new invalidations
    val reInvalidated = previousInvalidations.intersect(newTransitiveInvalidations)

    log.debug(
      s"Previously invalidated, but (transitively) depend on new invalidations:${ppxs(reInvalidated)}"
    )
    newInvalidations ++ reInvalidated
  }

  def ppxs[A](xs: Iterable[A]) = xs.iterator.map(x => s"\n\t$x").mkString

  /**
   * Logs API changes using debug-level logging. The API are obtained using the APIDiff class.
   *
   * NOTE: This method creates a new APIDiff instance on every invocation.
   */
  private def logApiChanges(
      apiChanges: Iterable[APIChange],
      oldAPIMapping: String => AnalyzedClass,
      newAPIMapping: String => AnalyzedClass
  ): Unit = {
    val contextSize = options.apiDiffContextSize
    try {
      val wrappedLog = new PrefixingLogger("[diff] ")(log)
      val apiDiff = new APIDiff
      apiChanges foreach {
        case APIChangeDueToMacroDefinition(src) =>
          wrappedLog.debug(s"Detected API change because $src contains a macro definition.")
        case TraitPrivateMembersModified(modifiedClass) =>
          wrappedLog.debug(s"Detect change in private members of trait ${modifiedClass}.")
        case apiChange: NamesChange =>
          val src = apiChange.modifiedClass
          val oldApi = oldAPIMapping(src)
          val newApi = newAPIMapping(src)
          val apiUnifiedPatch =
            apiDiff.generateApiDiff(src.toString, oldApi.api, newApi.api, contextSize)
          wrappedLog.debug(s"Detected a change in a public API ($src):\n$apiUnifiedPatch")
      }
    } catch {
      case e: Exception =>
        log.error("An exception has been thrown while trying to dump an api diff.")
        log.trace(e)
    }
  }

  /**
   * Add package objects that inherit from the set of invalidated classes to avoid
   * "class file needed by package is missing" compilation errors.
   *
   * This might be to conservative. We probably only need the package objects for packages
   * of invalidated classes.
   *
   * @param invalidatedClasses The set of invalidated classes.
   * @param relations The current relations.
   * @param apis The current APIs information.
   * @return The set of invalidated classes + the set of package objects.
   */
  protected def invalidatedPackageObjects(
      invalidatedClasses: Set[String],
      relations: Relations,
      apis: APIs
  ): Set[String]

  /**
   * Find an API change between the `previous` and `current` class representations of `className`.
   *
   * @param className The class name that identifies both analyzed classes.
   * @param previous The analyzed class that comes from the previous analysis.
   * @param current The analyzed class that comes from the current analysis.
   * @return An optional API change detected between `previous` and `current`.
   */
  protected def findAPIChange(
      className: String,
      previous: AnalyzedClass,
      current: AnalyzedClass
  ): Option[APIChange]

  /**
   * Finds the class dependencies of `className` given an instance of [[Relations]].
   *
   * @param className The class name from which we detect dependencies.
   * @param relations The instance of relations.
   * @return A collection of classes that depend on `className`.
   */
  protected def findClassDependencies(
      className: String,
      relations: Relations
  ): Set[String]

  /**
   * Invalidates a set of class names given the current relations and an internal API change.
   *
   * This step happens in every cycle of the incremental compiler as it is required to know
   * what classes were invalidated given the previous incremental compiler run.
   *
   * @param relations    The relations from the previous analysis file of the compiled project.
   * @param change       The internal API change detected by [[invalidateAfterInternalCompilation]].
   * @param isScalaClass A function that tell us whether a class is defined in a Scala file or not.
   */
  protected def invalidateClassesInternally(
      relations: Relations,
      change: APIChange,
      isScalaClass: String => Boolean
  ): Set[String]

  /**
   * Invalidates a set of class names given the current relations and an external API change
   * that has been detected in upstream projects. This step only happens in `invalidateInitial`
   * because that's where external changes need to be detected and properly invalidated.
   *
   * @param currentRelations The relations from the previous analysis file of the compiled project.
   * @param externalAPIChange The external API change detected by [[detectInitialChanges()]].
   * @param isScalaClass A function that tell us whether a class is defined in a Scala file or not.
   */
  protected def invalidateClassesExternally(
      currentRelations: Relations,
      externalAPIChange: APIChange,
      isScalaClass: String => Boolean
  ): Set[String]
}

object IncrementalCommon {

  /** Tell if given class names comes from a Scala source file or not by inspecting relations. */
  def comesFromScalaSource(
      previous: Relations,
      current: Option[Relations] = None
  )(className: String): Boolean = {
    val previousSourcesWithClassName = previous.classes.reverse(className)
    val newSourcesWithClassName = current.map(_.classes.reverse(className)).getOrElse(Set.empty)
    if (previousSourcesWithClassName.isEmpty && newSourcesWithClassName.isEmpty)
      sys.error(s"Fatal Zinc error: no entry for class $className in classes relation.")
    else {
      // Makes sure that the dependency doesn't possibly come from Java
      previousSourcesWithClassName.forall(src => APIUtil.isScalaSourceName(src.id)) &&
      newSourcesWithClassName.forall(src => APIUtil.isScalaSourceName(src.id))
    }
  }

  /** Invalidate all classes that claim to produce the same class file as another class. */
  def invalidateNamesProducingSameClassFile(merged: Relations): Set[String] = {
    merged.srcProd.reverseMap.flatMap { case (_, sources) =>
      if (sources.size > 1) sources.flatMap(merged.classNames(_)) else Nil
    }.toSet
  }

  /**
   * - If the classpath hash has NOT changed, check if there's been name shadowing
   *   by looking up the library-associated class names into the Analysis file.
   * - If the classpath hash has changed, check if the library-associated classes
   *   are still associated with the same library.
   *   This would avoid recompiling everything when classpath changes.
   *
   * @param lookup A lookup instance to ask questions about the classpath.
   * @param previousStamps The stamps associated with the previous compilation.
   * @param currentStamps The stamps associated with the current compilation.
   * @param previousRelations The relation from the previous compiler iteration.
   * @param log A logger.
   * @param equivS An equivalence function to compare stamps.
   * @return
   */
  def isLibraryModified(
      skipClasspathLookup: Boolean,
      lookup: Lookup,
      previousStamps: Stamps,
      currentStamps: ReadStamps,
      previousRelations: Relations,
      converter: FileConverter,
      log: Logger
  )(implicit equivS: Equiv[XStamp]): VirtualFileRef => Boolean = { (binaryFile: VirtualFileRef) =>
    {
      def invalidateBinary(reason: String): Boolean = {
        log.debug(s"Invalidating '$binaryFile' because $reason"); true
      }

      def compareStamps(previousFile: VirtualFileRef, currentFile: VirtualFileRef): Boolean = {
        val previousStamp = previousStamps.library(previousFile)
        val currentStamp = currentStamps.library(currentFile)
        if (equivS.equiv(previousStamp, currentStamp)) false
        else invalidateBinary(s"$previousFile ($previousStamp) != $currentFile ($currentStamp)")
      }

      def isLibraryChanged(file: VirtualFileRef): Boolean = {
        def compareOriginClassFile(className: String, classpathEntry: VirtualFileRef): Boolean = {
          if (
            classpathEntry.id.endsWith(".jar") &&
            (converter.toPath(classpathEntry).toString != converter.toPath(file).toString)
          )
            invalidateBinary(s"${className} is now provided by ${classpathEntry}")
          else compareStamps(file, classpathEntry)
        }

        val classNames = previousRelations.libraryClassNames(file)
        classNames.exists { binaryClassName =>
          if (lookup.changedClasspathHash.isEmpty) {
            // If classpath is not changed, the only possible change needs to come from same project
            lookup.lookupAnalysis(binaryClassName) match {
              case None => false
              // Most of the cases this is a build tool misconfiguration when using Zinc
              case Some(a) => invalidateBinary(s"${binaryClassName} came from analysis $a")
            }
          } else {
            // Find
            lookup.lookupOnClasspath(binaryClassName) match {
              case None =>
                invalidateBinary(s"could not find class $binaryClassName on the classpath.")
              case Some(classpathEntry) => compareOriginClassFile(binaryClassName, classpathEntry)
            }
          }
        }
      }

      if (skipClasspathLookup) compareStamps(binaryFile, binaryFile)
      else isLibraryChanged(binaryFile)
    }
  }

  def transitiveDeps[T](
      nodes: Iterable[T],
      log: Logger,
      logging: Boolean = true
  )(dependencies: T => Iterable[T]): Set[T] = {
    val visited = new collection.mutable.HashSet[T]
    def all(from: T, tos: Iterable[T]): Unit = tos.foreach(to => visit(from, to))
    def visit(from: T, to: T): Unit = {
      if (!visited.contains(to)) {
        if (logging) log.debug(s"Including $to by $from")
        visited += to
        all(to, dependencies(to))
      }
    }

    if (logging) log.debug(s"Initial set of included nodes: ${nodes.mkString(", ")}")
    nodes.foreach { start =>
      visited += start
      all(start, dependencies(start))
    }
    visited.toSet
  }

  /**
   * Check that a collection of files are absolute and not relative.
   *
   * For legacy reasons, the logic to check the absolute path of source files has been
   * implemented in the core invalidation algorithm logic. It remains here as there are
   * more important things to do than fixing this issue.
   *
   * @param addedSources
   */
  def checkAbsolute(addedSources: Iterable[VirtualFileRef]): Unit = {
    if (addedSources.isEmpty) ()
    else {
      // addedSources.filterNot(_.isAbsolute).toList match {
      //   case first :: more =>
      //     val fileStrings = more match {
      //       case Nil      => first.toString
      //       case x :: Nil => s"$first and $x"
      //       case _        => s"$first and ${more.size} others"
      //     }
      //     sys.error(s"Expected absolute source files instead of ${fileStrings}.")
      //   case Nil => ()
      // }
    }
  }

  lazy val emptyChanges: DependencyChanges = new DependencyChanges {
    override val modifiedBinaries = new Array[java.io.File](0)
    override val modifiedLibraries = new Array[VirtualFileRef](0)
    override val modifiedClasses = new Array[String](0)
    override def isEmpty = true
  }

  /**
   * Prunes from the analysis and deletes the class files of `invalidatedSources`.
   *
   * @param invalidatedSources The set of invalidated sources.
   * @param previous The previous analysis instance.
   * @param classfileManager The class file manager.
   * @return An instance of analysis that doesn't contain the invalidated sources.
   */
  def pruneClassFilesOfInvalidations(
      invalidatedSources: Set[VirtualFile],
      previous: Analysis,
      classfileManager: XClassFileManager,
      converter: FileConverter
  ): Analysis = {
    val products = invalidatedSources.flatMap(previous.relations.products).toList
    classfileManager.delete(products.map(converter.toVirtualFile(_)).toArray)
    previous -- invalidatedSources
  }
}
