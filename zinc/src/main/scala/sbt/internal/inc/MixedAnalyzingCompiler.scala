/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File
import java.lang.ref.{ SoftReference, Reference }
import java.util.Optional

import xsbti.{ Reporter, AnalysisCallback => XAnalysisCallback }
import xsbti.compile.CompileOrder._
import xsbti.compile._
import sbt.io.{ IO, DirectoryFilter }
import sbt.util.{ InterfaceUtil, Logger }
import sbt.internal.inc.JavaInterfaceUtil.EnrichOption
import sbt.internal.inc.caching.ClasspathCache
import sbt.internal.inc.javac.AnalyzingJavaCompiler
import xsbti.compile.{ ClassFileManager => XClassFileManager }

/** An instance of an analyzing compiler that can run both javac + scalac. */
final class MixedAnalyzingCompiler(
    val scalac: xsbti.compile.ScalaCompiler,
    val javac: AnalyzingJavaCompiler,
    val config: CompileConfiguration,
    val log: Logger
) {

  private[this] val absClasspath = toAbsolute(config.classpath)

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
      include: Set[File],
      changes: DependencyChanges,
      callback: XAnalysisCallback,
      classfileManager: XClassFileManager
  ): Unit = {
    val output = config.currentSetup.output
    val outputDirs = outputDirectories(output)
    outputDirs.foreach { d =>
      if (d.getName.endsWith(".jar"))
        IO.createDirectory(d.getParentFile)
      else {
        IO.createDirectory(d)
      }
    }

    val incSrc = config.sources.filter(include)
    val (javaSrcs, scalaSrcs) = incSrc.partition(javaOnly)
    logInputs(log, javaSrcs.size, scalaSrcs.size, outputDirs)

    /** Compile Scala sources. */
    def compileScala(): Unit =
      if (scalaSrcs.nonEmpty) {
        JarUtils.withPreviousJar(output) { extraClasspath =>
          val sources = if (config.currentSetup.order == Mixed) incSrc else scalaSrcs
          val arguments = cArgs(Nil,
                                toAbsolute(extraClasspath) ++ absClasspath,
                                None,
                                config.currentSetup.options.scalacOptions)
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
              config.progress.toOptional
            )
          }
        }
      }

    /** Compile java and run analysis. */
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
              IO.createDirectory(outputDir)
              javac.compile(javaSrcs,
                            joptions,
                            CompileOutput(outputDir),
                            Some(outputJar),
                            callback,
                            incToolOptions,
                            config.reporter,
                            log,
                            config.progress)
              putJavacOutputInJar(outputJar, outputDir)
            case None =>
              javac.compile(javaSrcs,
                            joptions,
                            output,
                            finalJarOutput = None,
                            callback,
                            incToolOptions,
                            config.reporter,
                            log,
                            config.progress)
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

    if (javaSrcs.size + scalaSrcs.size > 0)
      log.info("Done compiling.")
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
      JarUtils.OutputJarContent.addClasses(compiledClasses.map(_._2).toSet)
    }
    IO.delete(outputDir)
  }

  private def toAbsolute(extraClasspath: Seq[File]) = {
    extraClasspath.map(_.getAbsoluteFile)
  }

  private[this] def outputDirectories(output: Output): Seq[File] = {
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
      outputDirs: Seq[File]
  ): Unit = {
    val scalaMsg = Analysis.counted("Scala source", "", "s", scalaCount)
    val javaMsg = Analysis.counted("Java source", "", "s", javaCount)
    val combined = scalaMsg ++ javaMsg
    if (combined.nonEmpty) {
      val targets = outputDirs.map(_.getAbsolutePath).mkString(",")
      log.info(combined.mkString("Compiling ", " and ", s" to $targets ..."))
    }
  }

  /** Returns true if the file is java. */
  private[this] def javaOnly(f: File) = f.getName.endsWith(".java")
}

/**
 * Define helpers to create a wrapper around a Scala incremental compiler
 * [[xsbti.compile.ScalaCompiler]] and a Java incremental compiler
 * [[xsbti.compile.JavaCompiler]]. Note that the wrapper delegates to the
 * implementation of both compilers and only instructs how to run a cycle
 * of cross Java-Scala compilation.
 */
object MixedAnalyzingCompiler {
  def makeConfig(
      scalac: xsbti.compile.ScalaCompiler,
      javac: xsbti.compile.JavaCompiler,
      sources: Seq[File],
      classpath: Seq[File],
      output: Output,
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
      extra: List[(String, String)]
  ): CompileConfiguration = {
    val lookup = incrementalCompilerOptions.externalHooks().getExternalLookup

    def doHash: Array[FileHash] =
      ClasspathCache.hashClasspath(classpath)

    val classpathHash =
      if (lookup.isPresent) {
        val computed = lookup.get().hashClasspath(classpath.toArray)
        if (computed.isPresent) computed.get() else doHash
      } else doHash

    val compileSetup = MiniSetup.of(
      output,
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
      incrementalCompilerOptions
    )
  }

  def config(
      sources: Seq[File],
      classpath: Seq[File],
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
      incrementalCompilerOptions: IncOptions
  ): CompileConfiguration = {
    new CompileConfiguration(
      sources,
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
      incrementalCompilerOptions
    )
  }

  /** Returns the search classpath (for dependencies) and a function which can also do so. */
  def searchClasspathAndLookup(
      config: CompileConfiguration
  ): (Seq[File], String => Option[File]) = {
    import config._
    import currentSetup._
    // If we are compiling straight to jar, as javac does not support this,
    // it will be compiled to a temporary directory (with deterministic name)
    // and then added to the final jar. This temporary directory has to be
    // available for sbt.internal.inc.classfile.Analyze to work correctly.
    val tempJavacOutput =
      JarUtils.getOutputJar(currentSetup.output).map(JarUtils.javacTempOutput).toSeq
    val absClasspath = classpath.map(_.getAbsoluteFile)
    val cArgs =
      new CompilerArguments(compiler.scalaInstance, compiler.classpathOptions)
    val searchClasspath = explicitBootClasspath(options.scalacOptions) ++ withBootclasspath(
      cArgs,
      absClasspath
    ) ++ tempJavacOutput
    (searchClasspath, Locate.entry(searchClasspath, perClasspathEntryLookup))
  }

  /** Returns a "lookup file for a given class name" function. */
  def classPathLookup(config: CompileConfiguration): String => Option[File] =
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
      log
    )
  }

  def withBootclasspath(
      args: CompilerArguments,
      classpath: Seq[File]
  ): Seq[File] = {
    args.bootClasspathFor(classpath) ++ args.extClasspath ++
      args.finishClasspath(classpath)
  }

  private[this] def explicitBootClasspath(options: Seq[String]): Seq[File] = {
    options
      .dropWhile(_ != CompilerArguments.BootClasspathOption)
      .slice(1, 2)
      .headOption
      .toList
      .flatMap(IO.parseClasspath)
  }

  private[this] val cache =
    new collection.mutable.HashMap[File, Reference[AnalysisStore]]

  private def staticCache(
      file: File,
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
  def staticCachedStore(analysisFile: File, useTextAnalysis: Boolean): AnalysisStore = {
    import xsbti.compile.AnalysisStore
    val fileStore =
      if (useTextAnalysis) FileAnalysisStore.text(analysisFile)
      else FileAnalysisStore.binary(analysisFile)
    val cachedStore = AnalysisStore.getCachedStore(fileStore)
    staticCache(analysisFile, AnalysisStore.getThreadSafeStore(cachedStore))
  }
}
