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
import java.nio.file.{ Files, Path, Paths }
import java.util.{ EnumSet, UUID }
import sbt.internal.inc.Analysis.{ LocalProduct, NonLocalProduct }
import sbt.internal.inc.JavaInterfaceUtil.EnrichOption
import sbt.util.{ InterfaceUtil, Level, Logger }
import sbt.util.InterfaceUtil.{ jo2o, t2 }
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.collection.parallel.immutable.ParVector
import xsbti.{ FileConverter, Position, Problem, Severity, UseScope, VirtualFile, VirtualFileRef }
import xsbt.api.{ APIUtil, HashAPI, NameHashing }
import xsbti.api._
import xsbti.compile.{
  AnalysisContents,
  AnalysisStore,
  CompileAnalysis,
  CompileProgress,
  DependencyChanges,
  IncOptions,
  MiniSetup,
  Output,
  ClassFileManager => XClassFileManager
}
import xsbti.compile.analysis.{ ReadStamps, Stamp => XStamp }

/**
 * Helper methods for running incremental compilation.
 * This is responsible for is adapting any xsbti.AnalysisCallback into one
 * compatible with the [[sbt.internal.inc.Incremental]] class.
 */
object Incremental {
  class PrefixingLogger(val prefix: String)(orig: Logger) extends Logger {
    def trace(t: => Throwable): Unit = orig.trace(t)
    def success(message: => String): Unit = orig.success(message)
    def log(level: Level.Value, message: => String): Unit = level match {
      case Level.Debug => orig.log(level, message.replaceAll("(?m)^", prefix))
      case _           => orig.log(level, message)
    }
  }

  /**
   * This is a callback from AnalysisCallback back up to Zinc code to
   * perform mid-compilation.
   *
   * @param classFileManager
   */
  abstract class IncrementalCallback(classFileManager: XClassFileManager) {

    /**
     * Merge latest analysis as of pickling into pruned previous analysis, compute invalidations
     * and decide whether we need another cycle.
     */
    def mergeAndInvalidate(partialAnalysis: Analysis, completingCycle: Boolean): CompileCycleResult

    /**
     * Merge latest analysis as of analyzer into pruned previous analysis and inform file manager.
     */
    def completeCycle(
        prev: Option[CompileCycleResult],
        partialAnalysis: Analysis
    ): CompileCycleResult

    def previousAnalysisPruned: Analysis

    /**
     * @return true when the compilation cycle is compiling all the sources; false, otherwise.
     */
    def isFullCompilation: Boolean
  }

  sealed trait CompileCycle {
    def run(
        sources: Set[VirtualFile],
        changes: DependencyChanges,
        incHandler: IncrementalCallback
    ): CompileCycleResult
  }
  case class CompileCycleResult(
      continue: Boolean,
      nextInvalidations: Set[String],
      analysis: Analysis
  )
  object CompileCycleResult {
    def apply(
        continue: Boolean,
        nextInvalidations: Set[String],
        analysis: Analysis
    ): CompileCycleResult =
      new CompileCycleResult(continue, nextInvalidations, analysis)
    def empty = CompileCycleResult(false, Set.empty, Analysis.empty)
  }

  /**
   * Runs the incremental compilation algorithm.
   *
   * @param sources The full set of input sources
   * @param converter FileConverter to convert between Path and VirtualFileRef.
   * @param lookup An instance of the `Lookup` that implements looking up both classpath elements
   *               and Analysis object instances by a binary class name.
   * @param compile The mechanism to run a single 'step' of compile, for ALL source files involved.
   * @param previous0 The previous dependency Analysis (or an empty one).
   * @param output The configured output directory/directory mapping for source files.
   * @param log Where all log messages should go
   * @param options Incremental compiler options (like name hashing vs. not).
   * @return A flag of whether or not compilation completed successfully, and the resulting
   *         dependency analysis object.
   */
  def apply(
      sources: Set[VirtualFile],
      converter: FileConverter,
      lookup: Lookup,
      previous0: CompileAnalysis,
      options: IncOptions,
      currentSetup: MiniSetup,
      stamper: ReadStamps,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      earlyOutput: Option[Output],
      earlyAnalysisStore: Option[AnalysisStore],
      progress: Option[CompileProgress],
      log: Logger
  )(
      compile: (
          Set[VirtualFile],
          DependencyChanges,
          xsbti.AnalysisCallback,
          XClassFileManager
      ) => Unit
  ): (Boolean, Analysis) = {
    log.debug(s"[zinc] IncrementalCompile -----------")
    val previous = previous0 match { case a: Analysis => a }
    val currentStamper = Stamps.initial(stamper)
    val internalBinaryToSourceClassName = (binaryClassName: String) =>
      previous.relations.productClassName.reverse(binaryClassName).headOption
    val internalSourceToClassNamesMap: VirtualFile => Set[String] = (f: VirtualFile) =>
      previous.relations.classNames(f)
    val externalAPI = getExternalAPI(lookup)

    val earlyJar = for (early <- earlyOutput; jar <- jo2o(early.getSingleOutputAsPath)) yield jar
    val pickleJarPair = earlyJar.map { p =>
      val newName = s"${p.getFileName.toString.stripSuffix(".jar")}-${UUID.randomUUID()}.jar"
      val updatesJar = p.resolveSibling(newName)
      PickleJar.touch(updatesJar) // scalac should create -Ypickle-write jars but it throws FileNotFoundException :-/
      p -> updatesJar
    }

    val profiler = options.externalHooks.getInvalidationProfiler
    val runProfiler = new AdaptedRunProfiler(profiler.profileRun)
    val incremental: IncrementalCommon = new IncrementalNameHashing(log, options, runProfiler)
    try {
      incrementalCompile(
        sources,
        converter,
        lookup,
        previous,
        currentStamper,
        (vs, depCh, cb, cfm) => {
          val startTime = System.nanoTime()
          compile(vs, depCh, cb, cfm)
          runProfiler.timeCompilation(startTime, System.nanoTime() - startTime)
        },
        new AnalysisCallback.Builder(
          internalBinaryToSourceClassName,
          internalSourceToClassNamesMap,
          externalAPI,
          currentStamper,
          options,
          currentSetup,
          converter,
          lookup,
          output,
          outputJarContent,
          earlyOutput,
          earlyAnalysisStore,
          pickleJarPair,
          progress,
          log
        ),
        incremental,
        options,
        currentSetup,
        output,
        outputJarContent,
        earlyOutput,
        progress,
        log
      )
    } catch {
      case _: xsbti.CompileCancelled =>
        log.info("Compilation has been cancelled")
        // in case compilation got cancelled potential partial compilation results (e.g. produced classs files) got rolled back
        // and we can report back as there was no change (false) and return a previous Analysis which is still up-to-date
        (false, previous)
    } finally runProfiler.registerRun()
  }

  /**
   * Compile all Java sources, ignoring incrementality.
   * We are using Incremental class because we still need to perform Analysis so other subprojects
   * can do incremental compilation.
   */
  def compileAllJava(
      sources: Seq[VirtualFile],
      converter: FileConverter,
      lookup: Lookup,
      previous0: CompileAnalysis,
      options: IncOptions,
      currentSetup: MiniSetup,
      stamper: ReadStamps,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      earlyOutput: Option[Output],
      earlyAnalysisStore: Option[AnalysisStore],
      progress: Option[CompileProgress],
      log: Logger
  )(
      compileJava: (Seq[VirtualFile], xsbti.AnalysisCallback, XClassFileManager) => Unit
  ): (Boolean, Analysis) = {
    log.debug(s"[zinc] callAllJava")
    val previous = previous0 match { case a: Analysis => a }
    // prune Java knowledge out of previous Analysis
    val pruned = prune(sources.toSet, previous, output, outputJarContent, converter)
    val currentStamper = Stamps.initial(stamper)
    val internalBinaryToSourceClassName = (binaryClassName: String) =>
      pruned.relations.productClassName.reverse(binaryClassName).headOption
    val internalSourceToClassNamesMap: VirtualFile => Set[String] =
      (f: VirtualFile) => pruned.relations.classNames(f)
    val externalAPI = getExternalAPI(lookup)
    val builder = new AnalysisCallback.Builder(
      internalBinaryToSourceClassName,
      internalSourceToClassNamesMap,
      externalAPI,
      currentStamper,
      options,
      currentSetup,
      converter,
      lookup,
      output,
      outputJarContent,
      earlyOutput,
      earlyAnalysisStore,
      None,
      progress,
      log
    )
    // val profiler = options.externalHooks.getInvalidationProfiler
    // val runProfiler = new AdaptedRunProfiler(profiler.profileRun)
    // val incremental: IncrementalCommon = new IncrementalNameHashing(log, options, runProfiler)
    val callback = builder.build()
    try {
      val analysis = withClassfileManager(options, converter, output, outputJarContent) {
        classFileManager =>
          // See IncrementalCommon.scala's completeCycle
          def completeCycle(partialAnalysis: Analysis): Analysis = {
            val a1 = pruned ++ partialAnalysis
            val products = partialAnalysis.relations.allProducts
              .map(converter.toVirtualFile(_))
            classFileManager.generated(products.toArray)
            a1
          }
          compileJava(sources, callback, classFileManager)
          val a0 = callback.getPostJavaAnalysis
          completeCycle(a0)
      }
      (sources.nonEmpty, analysis)
    } catch {
      case _: xsbti.CompileCancelled =>
        log.info("Compilation has been cancelled")
        // in case compilation got cancelled potential partial compilation results (e.g. produced classs files) got rolled back
        // and we can report back as there was no change (false) and return a previous Analysis which is still up-to-date
        (false, previous)
    }
  }

  def getExternalAPI(lookup: Lookup): (VirtualFileRef, String) => Option[AnalyzedClass] =
    (_: VirtualFileRef, binaryClassName: String) => lookup.lookupAnalyzedClass(binaryClassName)

  /**
   * Runs the incremental compiler algorithm.
   *
   * @param sources   The sources to compile
   * @param converter FileConverter to convert between Path and VirtualFileRef.
   * @param lookup
   *              An instance of the `Lookup` that implements looking up both classpath elements
   *              and Analysis object instances by a binary class name.
   * @param previous0 The previous dependency Analysis (or an empty one).
   * @param current  A mechanism for generating stamps (timestamps, hashes, etc).
   * @param compile  The function which can run one level of compile.
   * @param callbackBuilder The builder that builds callback where we report dependency issues.
   * @param log  The log where we write debugging information
   * @param options  Incremental compilation options
   * @param outputJarContent Object that holds cached content of output jar
   * @param profiler An implementation of an invalidation profiler, empty by default.
   * @param equivS  The means of testing whether two "Stamps" are the same.
   * @return
   *         A flag of whether or not compilation completed successfully, and the resulting dependency analysis object.
   */
  def incrementalCompile(
      sources: Set[VirtualFile],
      converter: FileConverter,
      lookup: Lookup,
      previous0: CompileAnalysis,
      current: ReadStamps,
      compile: (
          Set[VirtualFile],
          DependencyChanges,
          xsbti.AnalysisCallback,
          XClassFileManager
      ) => Unit,
      callbackBuilder: AnalysisCallback.Builder,
      incremental: IncrementalCommon,
      options: IncOptions,
      currentSetup: MiniSetup,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      earlyOutput: Option[Output],
      progress: Option[CompileProgress],
      log: sbt.util.Logger
  )(implicit equivS: Equiv[XStamp]): (Boolean, Analysis) = {
    log.debug("IncrementalCompile.incrementalCompile")
    val previous = previous0 match { case a: Analysis => a }
    val initialChanges =
      incremental.detectInitialChanges(sources, previous, current, lookup, converter, output)
    log.debug(s"> initialChanges = $initialChanges")
    val binaryChanges = new DependencyChanges {
      override def modifiedBinaries: Array[File] =
        modifiedLibraries.map(converter.toPath(_).toFile)
      override val modifiedLibraries = initialChanges.libraryDeps.toArray
      override val modifiedClasses = initialChanges.external.allModified.toArray
      def isEmpty = modifiedLibraries.isEmpty && modifiedClasses.isEmpty
    }
    val (initialInvClasses, initialInvSources0) =
      incremental.invalidateInitial(previous.relations, initialChanges)

    // During pipelining, if there's any compilation at all, invalidate all Java sources too, so the downstream Scala subprojects would have type information via early output (pickle jar).
    val javaSources: Set[VirtualFileRef] = sources.collect {
      case s: VirtualFileRef if s.name.endsWith(".java") => s
    }
    val scalacOptions = currentSetup.options.scalacOptions
    val isPickleWrite = scalacOptions.contains("-Ypickle-write")
    if (isPickleWrite) {
      val idx = scalacOptions.indexOf("-Ypickle-write")
      val p =
        if (scalacOptions.size <= idx + 1) None
        else Some(Paths.get(scalacOptions(idx + 1)))
      val earlyJar = for {
        earlyO <- earlyOutput
        jar <- jo2o(earlyO.getSingleOutputAsPath())
      } yield jar
      (p, earlyJar) match {
        case (None, _)            => log.warn(s"-Ypickle-write is specified but <path> is not?")
        case (x1, x2) if x1 == x2 => ()
        case _ =>
          sys.error(
            s"early output must match -Ypickle-write path '$p' but was '$earlyJar' instead"
          )
      }
    } else if (!isPickleWrite && options.pipelining) {
      log.warn(s"-Ypickle-write should be included into scalacOptions if pipelining is enabled")
    }
    val isPickleJava = currentSetup.options.scalacOptions.contains("-Ypickle-java")
    if (javaSources.nonEmpty && options.pipelining && !isPickleJava) {
      log.warn(
        s"-Ypickle-java should be included into scalacOptions if pipelining is enabled with Java sources"
      )
    }
    val initialInvSources =
      if (isPickleJava && initialInvSources0.nonEmpty) initialInvSources0 ++ javaSources
      else initialInvSources0
    if (initialInvClasses.nonEmpty || initialInvSources.nonEmpty) {
      if (initialInvSources == sources)
        incremental.log.debug(s"all ${initialInvSources.size} sources are invalidated")
      else
        incremental.log.debug(
          "All initially invalidated classes: " + initialInvClasses + "\n" +
            "All initially invalidated sources:" + initialInvSources + "\n"
        )
    }
    val hasModified = initialInvClasses.nonEmpty || initialInvSources.nonEmpty
    val analysis = withClassfileManager(options, converter, output, outputJarContent) {
      classfileManager =>
        if (hasModified)
          incremental.cycle(
            initialInvClasses,
            initialInvSources,
            sources,
            converter,
            binaryChanges,
            lookup,
            previous,
            doCompile(compile, callbackBuilder, classfileManager),
            classfileManager,
            output,
            1
          )
        else {
          if (options.pipelining)
            writeEarlyOut(lookup, progress, earlyOutput, previous, new java.util.HashSet)
          previous
        }
    }
    (hasModified, analysis)
  }

  /**
   * Compilation unit in each compile cycle.
   */
  def doCompile(
      compile: (
          Set[VirtualFile],
          DependencyChanges,
          xsbti.AnalysisCallback,
          XClassFileManager
      ) => Unit,
      callbackBuilder: AnalysisCallback.Builder,
      classFileManager: XClassFileManager
  ): CompileCycle = new CompileCycle {
    override def run(
        srcs: Set[VirtualFile],
        changes: DependencyChanges,
        incHandler: IncrementalCallback
    ): CompileCycleResult = {
      // Note `ClassFileManager` is shared among multiple cycles in the same incremental compile run,
      // in order to rollback entirely if transaction fails. `AnalysisCallback` is used by each cycle
      // to report its own analysis individually.
      val callback = callbackBuilder.build(incHandler)
      compile(srcs, changes, callback, classFileManager)
      callback.getCycleResultOnce
    }
  }

  // the name of system property that was meant to enable debugging mode of incremental compiler but
  // it ended up being used just to enable debugging of relations. That's why if you migrate to new
  // API for configuring incremental compiler (IncOptions) it's enough to control value of `relationsDebug`
  // flag to achieve the same effect as using `incDebugProp`.
  @deprecated("Use `IncOptions.relationsDebug` flag to enable debugging of relations.", "0.13.2")
  val incDebugProp = "xsbt.inc.debug"

  private[inc] val apiDebugProp = "xsbt.api.debug"
  private[inc] def apiDebug(options: IncOptions): Boolean =
    options.apiDebug || java.lang.Boolean.getBoolean(apiDebugProp)

  private[sbt] def prune(
      invalidatedSrcs: Set[VirtualFile],
      previous0: CompileAnalysis,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      converter: FileConverter
  ): Analysis = {
    val previous = previous0.asInstanceOf[Analysis]
    IncrementalCommon.pruneClassFilesOfInvalidations(
      invalidatedSrcs,
      previous,
      ClassFileManager.deleteImmediately(output, outputJarContent),
      converter
    )
  }

  private[sbt] def withClassfileManager[T](
      options: IncOptions,
      converter: FileConverter,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent
  )(run: XClassFileManager => T): T = {
    val classfileManager =
      ClassFileManager.getClassFileManager(options, converter, output, outputJarContent)
    val result = try run(classfileManager)
    catch {
      case e: Throwable =>
        classfileManager.complete(false)
        throw e
    }
    classfileManager.complete(true)
    result
  }

  private[inc] def writeEarlyOut(
      lookup: Lookup,
      progress: Option[CompileProgress],
      earlyOutput: Option[Output],
      analysis: Analysis,
      knownProducts: java.util.Set[String]
  ) = {
    for {
      earlyO <- earlyOutput
      pickleJar <- jo2o(earlyO.getSingleOutputAsPath)
    } {
      PickleJar.write(pickleJar, knownProducts)
      progress.foreach(_.afterEarlyOutput(!lookup.shouldDoEarlyOutput(analysis)))
    }
  }
}

private object AnalysisCallback {

  /** Allow creating new callback instance to be used in each compile iteration */
  class Builder(
      internalBinaryToSourceClassName: String => Option[String],
      internalSourceToClassNamesMap: VirtualFile => Set[String],
      externalAPI: (VirtualFileRef, String) => Option[AnalyzedClass],
      stampReader: ReadStamps,
      options: IncOptions,
      currentSetup: MiniSetup,
      converter: FileConverter,
      lookup: Lookup,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      earlyOutput: Option[Output],
      earlyAnalysisStore: Option[AnalysisStore],
      pickleJarPair: Option[(Path, Path)],
      progress: Option[CompileProgress],
      log: Logger
  ) {
    def build(incHandler: Incremental.IncrementalCallback): AnalysisCallback =
      buildImpl(Some(incHandler))

    // Create an AnalysisCallback without IncHandler for Java compilation purpose.
    def build(): AnalysisCallback = buildImpl(None)

    private def buildImpl(incHandlerOpt: Option[Incremental.IncrementalCallback]) = {
      new AnalysisCallback(
        internalBinaryToSourceClassName,
        internalSourceToClassNamesMap,
        externalAPI,
        stampReader,
        options,
        currentSetup,
        outputJarContent,
        converter,
        lookup,
        output,
        earlyOutput,
        earlyAnalysisStore,
        pickleJarPair,
        progress,
        incHandlerOpt,
        log
      )
    }
  }

}

private final class AnalysisCallback(
    internalBinaryToSourceClassName: String => Option[String],
    internalSourceToClassNamesMap: VirtualFile => Set[String],
    externalAPI: (VirtualFileRef, String) => Option[AnalyzedClass],
    stampReader: ReadStamps,
    options: IncOptions,
    currentSetup: MiniSetup,
    outputJarContent: JarUtils.OutputJarContent,
    converter: FileConverter,
    lookup: Lookup,
    output: Output,
    earlyOutput: Option[Output],
    earlyAnalysisStore: Option[AnalysisStore],
    pickleJarPair: Option[(Path, Path)],
    progress: Option[CompileProgress],
    incHandlerOpt: Option[Incremental.IncrementalCallback],
    log: Logger
) extends xsbti.AnalysisCallback {
  import Incremental.CompileCycleResult

  // This must have a unique value per AnalysisCallback
  private[this] val compileStartTime: Long = System.currentTimeMillis()
  private[this] val compilation: Compilation = Compilation(compileStartTime, output)

  private val hooks = options.externalHooks
  private val provenance =
    jo2o(output.getSingleOutputAsPath).fold("")(hooks.getProvenance.get(_)).intern

  override def toString =
    (List("Class APIs", "Object APIs", "Library deps", "Products", "Source deps") zip
      List(classApis, objectApis, libraryDeps, nonLocalClasses, intSrcDeps))
      .map { case (label, map) => label + "\n\t" + map.mkString("\n\t") }
      .mkString("\n")

  case class ApiInfo(
      publicHash: HashAPI.Hash,
      extraHash: HashAPI.Hash,
      classLike: ClassLike
  )

  import java.util.concurrent.{ ConcurrentLinkedQueue, ConcurrentHashMap }
  import scala.collection.concurrent.TrieMap

  private type ConcurrentSet[A] = ConcurrentHashMap.KeySetView[A, java.lang.Boolean]

  private[this] val srcs = ConcurrentHashMap.newKeySet[VirtualFile]()
  private[this] val classApis = new TrieMap[String, ApiInfo]
  private[this] val objectApis = new TrieMap[String, ApiInfo]
  private[this] val classPublicNameHashes = new TrieMap[String, Array[NameHash]]
  private[this] val objectPublicNameHashes = new TrieMap[String, Array[NameHash]]
  private[this] val usedNames = new TrieMap[String, ConcurrentSet[UsedName]]
  private[this] val unreporteds = new TrieMap[VirtualFileRef, ConcurrentLinkedQueue[Problem]]
  private[this] val reporteds = new TrieMap[VirtualFileRef, ConcurrentLinkedQueue[Problem]]
  private[this] val mainClasses = new TrieMap[VirtualFileRef, ConcurrentLinkedQueue[String]]
  private[this] val libraryDeps = new TrieMap[VirtualFileRef, ConcurrentSet[VirtualFile]]

  // source file to set of generated (class file, binary class name); only non local classes are stored here
  private[this] val nonLocalClasses =
    new TrieMap[VirtualFileRef, ConcurrentSet[(VirtualFileRef, String)]]
  private[this] val localClasses = new TrieMap[VirtualFileRef, ConcurrentSet[VirtualFileRef]]
  // mapping between src class name and binary (flat) class name for classes generated from src file
  private[this] val classNames = new TrieMap[VirtualFileRef, ConcurrentSet[(String, String)]]
  // generated class file to its source class name
  private[this] val classToSource = new TrieMap[VirtualFileRef, String]
  // internal source dependencies
  private[this] val intSrcDeps = new TrieMap[String, ConcurrentSet[InternalDependency]]
  // external source dependencies
  private[this] val extSrcDeps = new TrieMap[String, ConcurrentSet[ExternalDependency]]
  private[this] val binaryClassName = new TrieMap[VirtualFile, String]
  // source files containing a macro def.
  private[this] val macroClasses = ConcurrentHashMap.newKeySet[String]()

  // Results of invalidation calculations (including whether to continue cycles) - the analysis at this point is
  // not useful and so isn't included.
  private[this] var invalidationResults: Option[CompileCycleResult] = None

  private def add[A, B](map: TrieMap[A, ConcurrentSet[B]], a: A, b: B): Unit = {
    map.getOrElseUpdate(a, ConcurrentHashMap.newKeySet[B]()).add(b)
    ()
  }

  override def isPickleJava: Boolean = {
    currentSetup.options.scalacOptions.contains("-Ypickle-java")
  }

  override def getPickleJarPair = pickleJarPair.map { case (p1, p2) => t2((p1, p2)) }.toOptional

  override def startSource(source: File): Unit = startSource(converter.toVirtualFile(source.toPath))
  override def startSource(source: VirtualFile): Unit = {
    if (options.strictMode()) {
      assert(
        !srcs.contains(source),
        s"The startSource can be called only once per source file: $source"
      )
    }
    srcs.add(source)
    ()
  }

  def problem(
      category: String,
      pos: Position,
      msg: String,
      severity: Severity,
      reported: Boolean
  ): Unit = {
    for (path <- jo2o(pos.sourcePath())) {
      val source = VirtualFileRef.of(path)
      val map = if (reported) reporteds else unreporteds
      map
        .getOrElseUpdate(source, new ConcurrentLinkedQueue)
        .add(InterfaceUtil.problem(category, pos, msg, severity, None))
    }
  }

  def classDependency(onClassName: String, sourceClassName: String, context: DependencyContext) = {
    if (onClassName != sourceClassName)
      add(intSrcDeps, sourceClassName, InternalDependency.of(sourceClassName, onClassName, context))
  }

  private[this] def externalLibraryDependency(
      binary: VirtualFile,
      className: String,
      source: VirtualFileRef,
      context: DependencyContext
  ): Unit = {
    binaryClassName.put(binary, className)
    add(libraryDeps, source, binary)
  }

  private[this] def externalSourceDependency(
      sourceClassName: String,
      targetBinaryClassName: String,
      targetClass: AnalyzedClass,
      context: DependencyContext
  ): Unit = {
    val dependency =
      ExternalDependency.of(sourceClassName, targetBinaryClassName, targetClass, context)
    add(extSrcDeps, sourceClassName, dependency)
  }

  // Called by sbt-dotty
  override def binaryDependency(
      classFile: File,
      onBinaryClassName: String,
      fromClassName: String,
      fromSourceFile: File,
      context: DependencyContext
  ): Unit =
    binaryDependency(
      classFile.toPath,
      onBinaryClassName,
      fromClassName,
      converter.toVirtualFile(fromSourceFile.toPath),
      context
    )

  // since the binary at this point could either *.class files or
  // library JARs, we need to accept Path here.
  override def binaryDependency(
      classFile: Path,
      onBinaryClassName: String,
      fromClassName: String,
      fromSourceFile: VirtualFileRef,
      context: DependencyContext
  ): Unit =
    internalBinaryToSourceClassName(onBinaryClassName) match {
      case Some(dependsOn) => // dependsOn is a source class name
        // dependency is a product of a source not included in this compilation
        classDependency(dependsOn, fromClassName, context)
      case None =>
        val vf = converter.toVirtualFile(classFile)
        classToSource.get(vf) match {
          case Some(dependsOn) =>
            // dependency is a product of a source in this compilation step,
            //  but not in the same compiler run (as in javac v. scalac)
            classDependency(dependsOn, fromClassName, context)
          case None =>
            externalDependency(classFile, onBinaryClassName, fromClassName, fromSourceFile, context)
        }
    }

  private[this] def externalDependency(
      classFile: Path,
      onBinaryName: String,
      sourceClassName: String,
      sourceFile: VirtualFileRef,
      context: DependencyContext
  ): Unit = {
    // TODO: handle library JARs and rt.jar.
    val vf = converter.toVirtualFile(classFile)
    externalAPI(vf, onBinaryName) match {
      case Some(api) =>
        // dependency is a product of a source in another project
        val targetBinaryClassName = onBinaryName
        externalSourceDependency(sourceClassName, targetBinaryClassName, api, context)
      case None =>
        // dependency is some other binary on the classpath.
        // exclude dependency tracking with rt.jar, for example java.lang.String -> rt.jar.
        if (vf.name != "rt.jar") {
          externalLibraryDependency(
            vf,
            onBinaryName,
            sourceFile,
            context
          )
        }
    }
  }

  // Called by sbt-dotty
  override def generatedNonLocalClass(
      source: File,
      classFile: File,
      binaryClassName: String,
      srcClassName: String
  ): Unit =
    generatedNonLocalClass(
      converter.toVirtualFile(source.toPath),
      classFile.toPath,
      binaryClassName,
      srcClassName
    )

  override def generatedNonLocalClass(
      source: VirtualFileRef,
      classFile: Path,
      binaryClassName: String,
      srcClassName: String
  ): Unit = {
    //println(s"Generated non local class ${source}, ${classFile}, ${binaryClassName}, ${srcClassName}")
    val vf = converter.toVirtualFile(classFile)
    add(nonLocalClasses, source, (vf, binaryClassName))
    add(classNames, source, (srcClassName, binaryClassName))
    classToSource.put(vf, srcClassName)
    ()
  }

  // Called by sbt-dotty
  override def generatedLocalClass(source: File, classFile: File): Unit =
    generatedLocalClass(converter.toVirtualFile(source.toPath), classFile.toPath)

  override def generatedLocalClass(source: VirtualFileRef, classFile: Path): Unit = {
    //println(s"Generated local class ${source}, ${classFile}")
    val vf = converter.toVirtualFile(classFile)
    add(localClasses, source, vf)
    ()
  }

  // Called by sbt-dotty
  override def api(sourceFile: File, classApi: ClassLike): Unit =
    api(converter.toVirtualFile(sourceFile.toPath), classApi)

  override def api(sourceFile: VirtualFileRef, classApi: ClassLike): Unit = {
    import xsbt.api.{ APIUtil, HashAPI }
    val className = classApi.name
    if (APIUtil.isScalaSourceName(sourceFile.id) && APIUtil.hasMacro(classApi))
      macroClasses.add(className)
    val shouldMinimize = !Incremental.apiDebug(options)
    val savedClassApi = if (shouldMinimize) APIUtil.minimize(classApi) else classApi
    val apiHash: HashAPI.Hash = HashAPI(classApi)
    val nameHashes = (new xsbt.api.NameHashing(options.useOptimizedSealed())).nameHashes(classApi)
    classApi.definitionType match {
      case d @ (DefinitionType.ClassDef | DefinitionType.Trait) =>
        val extraApiHash = {
          if (d != DefinitionType.Trait) apiHash
          else HashAPI(_.hashAPI(classApi), includePrivateDefsInTrait = true)
        }

        classApis(className) = ApiInfo(apiHash, extraApiHash, savedClassApi)
        classPublicNameHashes(className) = nameHashes.toArray
      case DefinitionType.Module | DefinitionType.PackageModule =>
        objectApis(className) = ApiInfo(apiHash, apiHash, savedClassApi)
        objectPublicNameHashes(className) = nameHashes.toArray
    }
  }

  // Called by sbt-dotty
  override def mainClass(sourceFile: File, className: String): Unit =
    mainClass(converter.toVirtualFile(sourceFile.toPath), className)

  override def mainClass(sourceFile: VirtualFileRef, className: String): Unit = {
    mainClasses.getOrElseUpdate(sourceFile, new ConcurrentLinkedQueue).add(className)
    ()
  }

  def usedName(className: String, name: String, useScopes: EnumSet[UseScope]) =
    add(usedNames, className, UsedName(name, useScopes))

  override def enabled(): Boolean = options.enabled

  private[this] var gotten: Boolean = false
  def getCycleResultOnce: CompileCycleResult = {
    assert(!gotten, "can't call AnalysisCallback#getCycleResultOnce more than once")
    val incHandler = incHandlerOpt.getOrElse(sys.error("incHandler was expected"))
    gotten = true
    // notify that early artifact writing is not going to happen because of macros
    def notifyEarlyArifactFailure(): Unit =
      if (!writtenEarlyArtifacts) {
        progress foreach { p =>
          p.afterEarlyOutput(false)
        }
      }
    outputJarContent.scalacRunCompleted()
    val a = getAnalysis
    if (options.pipelining) {
      invalidationResults match {
        case None =>
          val early = incHandler.previousAnalysisPruned
          if (!lookup.shouldDoEarlyOutput(early)) writeEarlyArtifacts(early)
          else notifyEarlyArifactFailure()
        case Some(CompileCycleResult(false, _, _)) => notifyEarlyArifactFailure()
        case _                                     => ()
      }
    }
    // assert(writtenEarlyArtifacts, s"early artifact $earlyOutput hasn't been written")
    incHandler.completeCycle(invalidationResults, a)
  }

  private def getAnalysis: Analysis = {
    val analysis0 = addProductsAndDeps(Analysis.empty)
    addUsedNames(addCompilation(analysis0))
  }

  def getPostJavaAnalysis: Analysis = {
    getAnalysis
  }

  def getOrNil[A, B](m: collection.Map[A, Seq[B]], a: A): Seq[B] = m.get(a).toList.flatten
  def addCompilation(base: Analysis): Analysis =
    base.copy(compilations = base.compilations.add(compilation))
  def addUsedNames(base: Analysis): Analysis = usedNames.foldLeft(base) {
    case (a, (className, names)) =>
      import scala.collection.JavaConverters._
      names.asScala.foldLeft(a) {
        case (a, name) => a.copy(relations = a.relations.addUsedName(className, name))
      }
  }

  private def companionsWithHash(className: String): (Companions, HashAPI.Hash, HashAPI.Hash) = {
    val emptyHash = -1
    val emptyClass =
      ApiInfo(emptyHash, emptyHash, APIUtil.emptyClassLike(className, DefinitionType.ClassDef))
    val emptyObject =
      ApiInfo(emptyHash, emptyHash, APIUtil.emptyClassLike(className, DefinitionType.Module))
    val ApiInfo(classApiHash, classHashExtra, classApi) = classApis.getOrElse(className, emptyClass)
    val ApiInfo(objectApiHash, objectHashExtra, objectApi) =
      objectApis.getOrElse(className, emptyObject)
    val companions = Companions.of(classApi, objectApi)
    val apiHash = (classApiHash, objectApiHash).hashCode
    val extraHash = (classHashExtra, objectHashExtra).hashCode
    (companions, apiHash, extraHash)
  }

  private def nameHashesForCompanions(className: String): Array[NameHash] = {
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
    val (companions, apiHash, extraHash) = companionsWithHash(name)
    val nameHashes = nameHashesForCompanions(name)
    val safeCompanions = SafeLazyProxy(companions)
    AnalyzedClass.of(
      compileStartTime,
      name,
      safeCompanions,
      apiHash,
      nameHashes,
      hasMacro,
      extraHash,
      provenance
    )
  }

  def addProductsAndDeps(base: Analysis): Analysis = {
    import scala.collection.JavaConverters._
    srcs.asScala.foldLeft(base) {
      case (a, src) =>
        val stamp = stampReader.source(src)
        val classesInSrc = classNames
          .getOrElse(src, ConcurrentHashMap.newKeySet[(String, String)]())
          .asScala
          .map(_._1)
        val analyzedApis = classesInSrc.map(analyzeClass)
        val info = SourceInfos.makeInfo(
          getOrNil(reporteds.iterator.map { case (k, v)   => k -> v.asScala.toSeq }.toMap, src),
          getOrNil(unreporteds.iterator.map { case (k, v) => k -> v.asScala.toSeq }.toMap, src),
          getOrNil(mainClasses.iterator.map { case (k, v) => k -> v.asScala.toSeq }.toMap, src)
        )
        val libraries: collection.mutable.Set[VirtualFile] =
          libraryDeps.getOrElse(src, ConcurrentHashMap.newKeySet[VirtualFile]).asScala
        val localProds = localClasses
          .getOrElse(src, ConcurrentHashMap.newKeySet[VirtualFileRef]())
          .asScala map { classFile =>
          val classFileStamp = stampReader.product(classFile)
          LocalProduct(classFile, classFileStamp)
        }
        val binaryToSrcClassName =
          (classNames.getOrElse(src, ConcurrentHashMap.newKeySet[(String, String)]()).asScala map {
            case (srcClassName, binaryClassName) => (binaryClassName, srcClassName)
          }).toMap
        val nonLocalProds = nonLocalClasses
          .getOrElse(src, ConcurrentHashMap.newKeySet[(VirtualFileRef, String)]())
          .asScala map {
          case (classFile, binaryClassName) =>
            val srcClassName = binaryToSrcClassName(binaryClassName)
            val classFileStamp = stampReader.product(classFile)
            NonLocalProduct(srcClassName, binaryClassName, classFile, classFileStamp)
        }

        val internalDeps = classesInSrc.flatMap(
          cls =>
            intSrcDeps.getOrElse(cls, ConcurrentHashMap.newKeySet[InternalDependency]()).asScala
        )
        val externalDeps = classesInSrc.flatMap(
          cls =>
            extSrcDeps.getOrElse(cls, ConcurrentHashMap.newKeySet[ExternalDependency]()).asScala
        )
        val libDeps = libraries.map(d => (d, binaryClassName(d), stampReader.library(d)))

        a.addSource(
          src,
          analyzedApis,
          stamp,
          info,
          nonLocalProds,
          localProds,
          internalDeps,
          externalDeps,
          libDeps
        )
    }
  }

  override def apiPhaseCompleted(): Unit = {
    // If we know we're done with cycles (presumably because all sources were invalidated) we can store early analysis
    // and picke data now.  Otherwise, we need to wait for dependency information to decide if there are more cycles.
    incHandlerOpt foreach { incHandler =>
      if (options.pipelining() && incHandler.isFullCompilation) {
        val a = getAnalysis
        val CompileCycleResult(continue, invalidations, merged) =
          incHandler.mergeAndInvalidate(a, false)
        if (!lookup.shouldDoEarlyOutput(merged)) {
          assert(
            !continue && invalidations.isEmpty,
            "everything was supposed to be invalidated already"
          )
          invalidationResults = Some(CompileCycleResult.empty)
          writeEarlyArtifacts(merged)
        }
      }
    }
  }

  override def dependencyPhaseCompleted(): Unit = {
    val incHandler = incHandlerOpt.getOrElse(sys.error("incHandler was expected"))
    if (invalidationResults.isEmpty) {
      val a = getAnalysis
      val CompileCycleResult(continue, invalidations, merged) =
        incHandler.mergeAndInvalidate(a, false)
      // Store invalidations and continuation decision; the analysis will be computed again after Analyze phase.
      invalidationResults = Some(CompileCycleResult(continue, invalidations, Analysis.empty))
      // If there will be no more compilation cycles, store the early analysis file and update the pickle jar
      if (options.pipelining && !continue && !lookup.shouldDoEarlyOutput(merged)) {
        writeEarlyArtifacts(merged)
      }
    }
    outputJarContent.dependencyPhaseCompleted()
  }

  override def classesInOutputJar(): java.util.Set[String] = {
    outputJarContent.get().asJava
  }

  private[this] var writtenEarlyArtifacts: Boolean = false

  private def writeEarlyArtifacts(merged: Analysis): Unit = {
    writtenEarlyArtifacts = true
    earlyAnalysisStore.foreach(_.set(AnalysisContents.create(merged, currentSetup)))
    pickleJarPair.foreach {
      case (originalJar, updatesJar) =>
        if (Files.exists(originalJar) && Files.exists(updatesJar)) {
          log.debug(s"merging $updatesJar into $originalJar")
          IndexBasedZipFsOps.mergeArchives(originalJar, updatesJar)
        } else if (Files.exists(updatesJar)) {
          log.debug(s"moving $updatesJar to $originalJar")
          import java.nio.file.StandardCopyOption.REPLACE_EXISTING
          Files.move(updatesJar, originalJar, REPLACE_EXISTING)
        }
    }
    Incremental.writeEarlyOut(lookup, progress, earlyOutput, merged, knownProducts(merged))
  }

  private def knownProducts(merged: Analysis) = {
    // List classes defined in the files that were compiled in this run.
    val ps = java.util.concurrent.ConcurrentHashMap.newKeySet[String]
    val knownProducts: ParVector[VirtualFileRef] =
      new ParVector(merged.relations.allSources.toVector)
        .flatMap(merged.relations.products)
    // extract product paths in parallel
    jo2o(output.getSingleOutputAsPath) match {
      case Some(so) if so.getFileName.toString.endsWith(".jar") =>
        knownProducts foreach { product =>
          new JarUtils.ClassInJar(product.id).toClassFilePath foreach { path =>
            ps.add(path.replace('\\', '/'))
          }
        }
      case Some(so) =>
        knownProducts foreach { product =>
          val productPath = converter.toPath(product)
          try {
            ps.add(so.relativize(productPath).toString.replace('\\', '/'))
          } catch {
            case NonFatal(_) => ps.add(product.id)
          }
        }
      case _ => sys.error(s"unsupported output $output")
    }
    ps
  }
}
