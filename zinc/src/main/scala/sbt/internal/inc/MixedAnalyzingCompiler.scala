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
import java.nio.file.{ Files, Path }
import java.lang.ref.{ SoftReference, Reference }
import java.util.Optional

import xsbti.{
  FileConverter,
  Reporter,
  AnalysisCallback => XAnalysisCallback,
  VirtualFile,
  VirtualFileRef
}
import xsbti.compile._
import xsbti.compile.CompileOrder._
import xsbti.compile.{ ClassFileManager => XClassFileManager }
import xsbti.compile.analysis.ReadStamps
import sbt.io.{ IO, DirectoryFilter }
import sbt.util.{ InterfaceUtil, Logger }
import sbt.internal.inc.JavaInterfaceUtil.EnrichOption
import sbt.internal.inc.VirtualFileUtil.toAbsolute
import sbt.internal.inc.caching.ClasspathCache
import sbt.internal.inc.javac.AnalyzingJavaCompiler
import sbt.internal.util.ConsoleAppender

/** An instance of an analyzing compiler that can run both javac + scalac. */
final class MixedAnalyzingCompiler(
    val scalac: xsbti.compile.ScalaCompiler,
    val javac: AnalyzingJavaCompiler,
    val config: CompileConfiguration,
    val log: Logger,
    outputJarContent: JarUtils.OutputJarContent
) {
  private[this] val absClasspath = config.classpath.map(toAbsolute(_))

  /** Mechanism to work with compiler arguments. */
  private[this] val cArgs =
    new CompilerArguments(config.compiler.scalaInstance, config.compiler.classpathOptions)

  /**
   * Compiles the given Java/Scala files.
   *
   * @param include          The files to compile right now
   * @param changes          A list of dependency changes.
   * @param callback         The callback where we report dependency issues.
   * @param classfileManager The component that manages generated class files.
   */
  def compile(
      include: Set[VirtualFile],
      changes: DependencyChanges,
      callback: XAnalysisCallback,
      classfileManager: XClassFileManager
  ): Unit = compile(include, changes, callback, classfileManager, config.progress)

  /**
   * Compiles the given Java/Scala files.
   *
   * @param include          The files to compile right now
   * @param changes          A list of dependency changes.
   * @param callback         The callback where we report dependency issues.
   * @param classfileManager The component that manages generated class files.
   */
  def compile(
      include: Set[VirtualFile],
      changes: DependencyChanges,
      callback: XAnalysisCallback,
      classfileManager: XClassFileManager,
      progress: Option[CompileProgress]
  ): Unit = {
    val output = config.output
    val outputDirs = outputDirectories(output)
    outputDirs.foreach { d =>
      if (d.toString.endsWith(".jar"))
        Files.createDirectories(d.getParent)
      else {
        Files.createDirectories(d)
      }
    }

    val incSrc = config.sources.filter(include)
    val (javaSrcs, scalaSrcs) = incSrc.partition(javaOnly(_))
    logInputs(log, javaSrcs.size, scalaSrcs.size, outputDirs)

    // Compile Scala sources.
    def compileScala(): Unit =
      if (scalaSrcs.nonEmpty) {
        JarUtils.withPreviousJar(output) { extraClasspath: Seq[Path] =>
          val sources =
            if (config.currentSetup.order == Mixed) incSrc
            else scalaSrcs

          val cp: Seq[VirtualFile] = (extraClasspath map { x =>
            config.converter.toVirtualFile(x.toAbsolutePath)
          }) ++ absClasspath
          val arguments =
            cArgs.makeArguments(Nil, cp, config.currentSetup.options.scalacOptions)
          timed("Scala compilation", log) {
            config.compiler.compile(
              sources.toArray,
              changes,
              arguments.toArray,
              output,
              callback,
              config.reporter,
              config.cache,
              log,
              progress.toOptional
            )
          }
        }
      }

    // Compile java and run analysis.
    def compileJava(): Unit = {
      if (javaSrcs.nonEmpty) {
        timed("Java compilation + analysis", log) {
          val incToolOptions =
            IncToolOptions.of(
              Optional.of(classfileManager),
              config.incOptions.useCustomizedFileManager()
            )
          val joptions = config.currentSetup.options.javacOptions

          JarUtils.getOutputJar(output) match {
            case Some(outputJar) =>
              val outputDir = JarUtils.javacTempOutput(outputJar)
              Files.createDirectories(outputDir)
              javac.compile(
                javaSrcs,
                config.converter,
                joptions,
                CompileOutput(outputDir),
                Some(outputJar),
                callback,
                incToolOptions,
                config.reporter,
                log,
                config.progress
              )
              putJavacOutputInJar(outputJar.toFile, outputDir.toFile)
            case None =>
              javac.compile(
                javaSrcs,
                config.converter,
                joptions,
                output,
                finalJarOutput = None,
                callback,
                incToolOptions,
                config.reporter,
                log,
                config.progress
              )
          }
        }
      }
    }

    /* `Mixed` order defaults to `ScalaThenJava` behaviour.
     * See https://github.com/sbt/zinc/issues/234. */
    if (config.currentSetup.order == JavaThenScala) {
      compileJava(); compileScala()
    } else {
      compileScala(); compileJava()
    }

    if (javaSrcs.size + scalaSrcs.size > 0) {
      if (ConsoleAppender.showProgress) log.debug("Done compiling.")
      else log.info("Done compiling.")
    }
  }

  private def putJavacOutputInJar(outputJar: File, outputDir: File): Unit = {
    import sbt.io.syntax._
    val compiledClasses = (outputDir ** -DirectoryFilter).get.flatMap { classFile =>
      IO.relativize(outputDir, classFile) match {
        case Some(relPath) =>
          List((classFile, relPath))
        case _ => Nil
      }
    }

    if (compiledClasses.nonEmpty) {
      JarUtils.includeInJar(outputJar, compiledClasses)
      outputJarContent.addClasses(compiledClasses.map(_._2).toSet)
    }
    IO.delete(outputDir)
  }

  private[this] def outputDirectories(output: Output): Seq[Path] = {
    output match {
      case single: SingleOutput => List(single.getOutputDirectory)
      case mult: MultipleOutput => mult.getOutputGroups map (_.getOutputDirectory)
    }
  }

  // Debugging method to time how long it takes to run various compilation tasks.
  private[this] def timed[T](label: String, log: Logger)(t: => T): T = {
    val start = System.nanoTime
    val result = t
    val elapsed = System.nanoTime - start
    log.debug(label + " took " + (elapsed / 1e9) + " s")
    result
  }

  private[this] def logInputs(
      log: Logger,
      javaCount: Int,
      scalaCount: Int,
      outputDirs: Seq[Path]
  ): Unit = {
    val scalaMsg = Analysis.counted("Scala source", "", "s", scalaCount)
    val javaMsg = Analysis.counted("Java source", "", "s", javaCount)
    val combined = scalaMsg ++ javaMsg
    if (combined.nonEmpty) {
      val targets = outputDirs.map(_.toAbsolutePath).mkString(",")
      log.info(combined.mkString("Compiling ", " and ", s" to $targets ..."))
    }
  }

  /** Returns true if the file is java. */
  private[this] def javaOnly(f: VirtualFileRef): Boolean = f.id.endsWith(".java")
}

/**
 * Define helpers to create a wrapper around a Scala incremental compiler
 * `xsbti.compile.ScalaCompiler` and a Java incremental compiler
 * `xsbti.compile.JavaCompiler`. Note that the wrapper delegates to the
 * implementation of both compilers and only instructs how to run a cycle
 * of cross Java-Scala compilation.
 */
object MixedAnalyzingCompiler {
  def makeConfig(
      scalac: xsbti.compile.ScalaCompiler,
      javac: xsbti.compile.JavaCompiler,
      sources: Seq[VirtualFile],
      converter: FileConverter, // this is needed to thaw ref back to path for stamping
      classpath: Seq[VirtualFile],
      cache: GlobalsCache,
      progress: Option[CompileProgress] = None,
      options: Seq[String] = Nil,
      javacOptions: Seq[String] = Nil,
      previousAnalysis: CompileAnalysis,
      previousSetup: Option[MiniSetup],
      perClasspathEntryLookup: PerClasspathEntryLookup,
      reporter: Reporter,
      compileOrder: CompileOrder = Mixed,
      skip: Boolean = false,
      incrementalCompilerOptions: IncOptions,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      earlyOutput: Option[Output],
      earlyAnalysisStore: Option[AnalysisStore],
      stamper: ReadStamps,
      extra: List[(String, String)]
  ): CompileConfiguration = {
    val lookup = incrementalCompilerOptions.externalHooks().getExternalLookup

    def doHash: Array[FileHash] =
      ClasspathCache.hashClasspath(classpath.map(converter.toPath))

    val classpathHash =
      if (lookup.isPresent) {
        val computed = lookup.get().hashClasspath(classpath.toArray)
        if (computed.isPresent) computed.get() else doHash
      } else doHash

    val compileSetup = MiniSetup.of(
      output, // MiniSetup gets persisted into Analysis so don't use this
      MiniOptions.of(
        classpathHash,
        options.toArray,
        javacOptions.toArray
      ),
      scalac.scalaInstance.actualVersion,
      compileOrder,
      incrementalCompilerOptions.storeApis(),
      (extra map InterfaceUtil.t2).toArray
    )
    config(
      sources,
      converter,
      classpath,
      compileSetup,
      progress,
      previousAnalysis,
      previousSetup,
      perClasspathEntryLookup,
      scalac,
      javac,
      reporter,
      skip,
      cache,
      incrementalCompilerOptions,
      output,
      outputJarContent,
      earlyOutput,
      earlyAnalysisStore,
      stamper
    )
  }

  def config(
      sources: Seq[VirtualFile],
      converter: FileConverter,
      classpath: Seq[VirtualFile],
      setup: MiniSetup,
      progress: Option[CompileProgress],
      previousAnalysis: CompileAnalysis,
      previousSetup: Option[MiniSetup],
      perClasspathEntryLookup: PerClasspathEntryLookup,
      compiler: xsbti.compile.ScalaCompiler,
      javac: xsbti.compile.JavaCompiler,
      reporter: Reporter,
      skip: Boolean,
      cache: GlobalsCache,
      incrementalCompilerOptions: IncOptions,
      output: Output,
      outputJarContent: JarUtils.OutputJarContent,
      earlyOutput: Option[Output],
      earlyAnalysisStore: Option[AnalysisStore],
      stamper: ReadStamps,
  ): CompileConfiguration = {
    new CompileConfiguration(
      sources,
      converter,
      classpath,
      previousAnalysis,
      previousSetup,
      setup,
      progress,
      perClasspathEntryLookup: PerClasspathEntryLookup,
      reporter,
      compiler,
      javac,
      cache,
      incrementalCompilerOptions,
      output,
      outputJarContent,
      earlyOutput,
      earlyAnalysisStore,
      stamper
    )
  }

  /** Returns the search classpath (for dependencies) and a function which can also do so. */
  def searchClasspathAndLookup(
      config: CompileConfiguration
  ): (Seq[VirtualFile], String => Option[VirtualFile]) = {
    import config._
    import currentSetup._
    // If we are compiling straight to jar, as javac does not support this,
    // it will be compiled to a temporary directory (with deterministic name)
    // and then added to the final jar. This temporary directory has to be
    // available for sbt.internal.inc.classfile.Analyze to work correctly.
    val tempJavacOutput =
      JarUtils
        .getOutputJar(config.output)
        .map(JarUtils.javacTempOutput)
        .toSeq
        .map(converter.toVirtualFile(_))
    val absClasspath = classpath.map(toAbsolute(_))
    val cArgs =
      new CompilerArguments(compiler.scalaInstance, compiler.classpathOptions)
    val searchClasspath
        : Seq[VirtualFile] = explicitBootClasspath(options.scalacOptions, converter) ++
      withBootclasspath(
        cArgs,
        absClasspath,
        converter
      ) ++ tempJavacOutput
    (searchClasspath, Locate.entry(searchClasspath, perClasspathEntryLookup))
  }

  /** Returns a "lookup file for a given class name" function. */
  def classPathLookup(config: CompileConfiguration): String => Option[VirtualFile] =
    searchClasspathAndLookup(config)._2

  def apply(config: CompileConfiguration)(
      implicit log: Logger
  ): MixedAnalyzingCompiler = {
    import config._
    val (searchClasspath, entry) = searchClasspathAndLookup(config)
    // Construct a compiler which can handle both java and scala sources.
    new MixedAnalyzingCompiler(
      compiler,
      // TODO - Construction of analyzing Java compiler MAYBE should be earlier...
      new AnalyzingJavaCompiler(
        javac,
        classpath,
        compiler.scalaInstance,
        compiler.classpathOptions,
        entry,
        searchClasspath
      ),
      config,
      log,
      outputJarContent
    )
  }

  def withBootclasspath(
      args: CompilerArguments,
      classpath: Seq[VirtualFile],
      converter: FileConverter
  ): Seq[VirtualFile] = {
    val cp: Seq[Path] = classpath.map(converter.toPath)
    args.bootClasspathFor(cp).map(converter.toVirtualFile(_)) ++
      args.extClasspath.map(PlainVirtualFile(_)) ++
      args.finishClasspath(cp).map(converter.toVirtualFile(_))
  }

  private[this] def explicitBootClasspath(
      options: Seq[String],
      converter: FileConverter
  ): Seq[VirtualFile] = {
    options
      .dropWhile(_ != CompilerArguments.BootClasspathOption)
      .slice(1, 2)
      .headOption
      .toList
      .flatMap(IO.parseClasspath)
      .map(_.toPath)
      .map(converter.toVirtualFile(_))
  }

  private[this] val cache =
    new collection.mutable.HashMap[Path, Reference[AnalysisStore]]

  private def staticCache(
      file: Path,
      backing: => AnalysisStore
  ): AnalysisStore = {
    synchronized {
      cache get file flatMap { ref =>
        Option(ref.get)
      } getOrElse {
        val b = backing
        cache.put(file, new SoftReference(b))
        b
      }
    }
  }

  /**
   * Create a an analysis store cache at the desired location.
   *
   * Note: This method will be deprecated after Zinc 1.1.
   */
  def staticCachedStore(analysisFile: Path, useTextAnalysis: Boolean): AnalysisStore = {
    import xsbti.compile.AnalysisStore
    val fileStore =
      if (useTextAnalysis) sbt.internal.inc.FileAnalysisStore.text(analysisFile.toFile)
      else sbt.internal.inc.FileAnalysisStore.binary(analysisFile.toFile)
    val cachedStore = AnalysisStore.getCachedStore(fileStore)
    staticCache(analysisFile, AnalysisStore.getThreadSafeStore(cachedStore))
  }
}
