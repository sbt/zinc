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
import xsbti.compile.CompileOrder._
import xsbti.compile.{ ClassFileManager => XClassFileManager, FileAnalysisStore => _, _ }
import xsbti.compile.analysis.{ ReadStamps, ReadWriteMappers }
import sbt.io.{ IO, DirectoryFilter }
import sbt.util.{ InterfaceUtil, Logger }
import sbt.internal.inc.consistent.ConsistentFileAnalysisStore
import sbt.internal.inc.JavaInterfaceUtil.EnrichOption
import sbt.internal.inc.JavaInterfaceUtil.EnrichOptional
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

  /**
   * Compile java and run analysis.
   */
  def compileJava(
      javaSrcs: Seq[VirtualFile],
      callback: XAnalysisCallback,
      classfileManager: XClassFileManager,
  ): Unit = {
    ensureOutput
    if (javaSrcs.nonEmpty)
      timed("Java compilation + analysis", log) {
        val output = config.currentSetup.output
        val incToolOptions =
          IncToolOptions.of(
            Optional.of(classfileManager),
            config.incOptions.useCustomizedFileManager()
          )
        val joptions = config.currentSetup.options.javacOptions

        def toVirtualFile(p: Path) = config.converter.toVirtualFile(p.toAbsolutePath)

        val outputJarOpt = JarUtils.getOutputJar(output)
        outputJarOpt match {
          case Some(outputJar) if !javac.supportsDirectToJar =>
            val outputDir = JarUtils.javacTempOutput(outputJar)
            Files.createDirectories(outputDir)
            javac.compile(
              javaSrcs,
              Seq(toVirtualFile(outputJar)),
              config.converter,
              joptions.toIndexedSeq,
              CompileOutput(outputDir),
              outputJarOpt,
              callback,
              incToolOptions,
              config.reporter,
              log,
              config.progress
            )
            putJavacOutputInJar(outputJar.toFile, outputDir.toFile)
          case _ =>
            JarUtils.withPreviousJar(output) { extraClasspath: Seq[Path] =>
              javac.compile(
                javaSrcs,
                extraClasspath map toVirtualFile,
                config.converter,
                joptions.toIndexedSeq,
                output,
                outputJarOpt,
                callback,
                incToolOptions,
                config.reporter,
                log,
                config.progress
              )
            }
        }
      } // timed
    else ()
  }

  // We had this as lazy val, but that caused issues https://github.com/sbt/sbt/issues/5951
  def ensureOutput = {
    val output = config.currentSetup.output
    JarUtils.getOutputJar(output) match {
      case Some(jar) =>
        Files.createDirectories(jar.getParent)
        Seq(jar)
      case None =>
        val dirs = outputDirectories(output)
        dirs.foreach(Files.createDirectories(_))
        dirs
    }
  }

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
  ): Unit = {
    val output = config.currentSetup.output
    val outputDirs = ensureOutput

    val incSrc = config.sources.filter(include)
    val (javaSrcs, scalaSrcs) = incSrc.partition(MixedAnalyzingCompiler.javaOnly)
    logInputs(log, javaSrcs.size, scalaSrcs.size, outputDirs)
    val pickleJava =
      Incremental.isPickleJava(config.currentSetup.options.scalacOptions.toIndexedSeq)

    // Compile Scala sources.
    def compileScala(): Unit =
      if (scalaSrcs.nonEmpty || pickleJava) {
        val pickleJarPair = callback.getPickleJarPair.toOption.map(t2 => (t2.get1, t2.get2))
        val scalacOpts = pickleJarPair match {
          case Some((originalJar, updatesJar)) =>
            val path = originalJar.toString
            // ^ Path#toString uses '\' on Windows
            // but the path could've been specified with '/' in scalacOptions
            val fwdSlashPath = path.replace('\\', '/')
            config.currentSetup.options.scalacOptions.map {
              case s if s == path || s == fwdSlashPath => updatesJar.toString
              case s                                   => s
            }
          case _ => config.currentSetup.options.scalacOptions
        }

        JarUtils.withPreviousJar(output) { extraClasspath: Seq[Path] =>
          val sources =
            if (config.currentSetup.order == Mixed) incSrc
            else scalaSrcs

          val cp0: Vector[VirtualFile] =
            (extraClasspath.toVector map { x =>
              config.converter.toVirtualFile(x.toAbsolutePath)
            }) ++ absClasspath.toVector
          val cp =
            if (scalaSrcs.isEmpty && pickleJava) {
              // we are invoking Scala compiler just for the sake of generating pickles for Java, which
              // means that the classpath would not contain scala-library jar from the build tool.
              // to work around this, we inject the scala-library into the classpath
              val libraryJars = scalac.scalaInstance.libraryJars.toVector map { x =>
                config.converter.toVirtualFile(x.toPath)
              }
              (cp0 ++ libraryJars).distinct
            } else cp0
          timed("Scala compilation", log) {
            config.compiler.compile(
              sources.toArray,
              cp.toArray,
              config.converter,
              changes,
              scalacOpts.toArray,
              output,
              callback,
              config.reporter,
              config.progress.toOptional,
              log
            )
          }
        }
      }
    def compileJava0(): Unit = compileJava(javaSrcs, callback, classfileManager)

    if (config.incOptions.pipelining) {
      compileScala()
      if (scalaSrcs.nonEmpty) {
        log.debug("done compiling Scala sources")
      }
    } else {
      /* `Mixed` order defaults to `ScalaThenJava` behaviour.
       * See https://github.com/sbt/zinc/issues/234. */
      if (config.currentSetup.order == JavaThenScala) {
        compileJava0(); compileScala()
      } else {
        compileScala(); compileJava0()
      }
      if (javaSrcs.size + scalaSrcs.size > 0) {
        if (ConsoleAppender.showProgress) log.debug("done compiling")
        else log.info("done compiling")
      }
    }

  }

  private def putJavacOutputInJar(outputJar: File, outputDir: File): Unit = {
    import sbt.io.syntax._
    val compiledClasses = (outputDir ** -DirectoryFilter).get().flatMap { classFile =>
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
      case single: SingleOutput => List(single.getOutputDirectoryAsPath)
      case mult: MultipleOutput =>
        mult.getOutputGroups.toIndexedSeq map (_.getOutputDirectoryAsPath)
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
      log.info(combined.mkString("compiling ", " and ", s" to $targets ..."))
    }
  }
}

/**
 * Define helpers to create a wrapper around a Scala incremental compiler
 * `xsbti.compile.ScalaCompiler` and a Java incremental compiler
 * `xsbti.compile.JavaCompiler`. Note that the wrapper delegates to the
 * implementation of both compilers and only instructs how to run a cycle
 * of cross Java-Scala compilation.
 */
object MixedAnalyzingCompiler {

  /** Returns true if the file is java. */
  private[sbt] def javaOnly(f: VirtualFileRef): Boolean = f.id.endsWith(".java")

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
      outputJarContent,
      earlyOutput,
      earlyAnalysisStore,
      stamper
    )
  }

  /** Returns the search classpath (for dependencies) and a function which can also do so. */
  def searchClasspathAndLookup(
      config: CompileConfiguration
  ): (Seq[VirtualFile], String => Option[VirtualFile]) =
    searchClasspathAndLookup(
      config.converter,
      config.classpath,
      config.currentSetup.options.scalacOptions,
      config.perClasspathEntryLookup,
      config.compiler
    )

  /** Returns the search classpath (for dependencies) and a function which can also do so. */
  def searchClasspathAndLookup(
      converter: FileConverter,
      classpath: Seq[VirtualFile],
      scalacOptions: Array[String],
      perClasspathEntryLookup: PerClasspathEntryLookup,
      compiler: xsbti.compile.ScalaCompiler
  ): (Seq[VirtualFile], String => Option[VirtualFile]) = {
    val absClasspath = classpath.map(toAbsolute(_))
    val cArgs =
      new CompilerArguments(compiler.scalaInstance, compiler.classpathOptions)
    val searchClasspath: Seq[VirtualFile] = explicitBootClasspath(
      scalacOptions.toIndexedSeq,
      converter
    ) ++
      withBootclasspath(
        cArgs,
        absClasspath,
        converter
      )
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
   */
  def staticCachedStore(analysisFile: Path, useTextAnalysis: Boolean): AnalysisStore =
    staticCachedStore(
      analysisFile = analysisFile,
      useTextAnalysis = useTextAnalysis,
      useConsistent = false,
      mappers = ReadWriteMappers.getEmptyMappers(),
      reproducible = true,
      parallelism = Runtime.getRuntime.availableProcessors(),
    )

  def staticCachedStore(
      analysisFile: Path,
      useTextAnalysis: Boolean,
      useConsistent: Boolean,
      mappers: ReadWriteMappers,
      reproducible: Boolean,
      parallelism: Int,
  ): AnalysisStore = {
    val fileStore = (useTextAnalysis, useConsistent) match {
      case (false, false) =>
        FileAnalysisStore.binary(analysisFile.toFile, mappers)
      case (false, true) =>
        ConsistentFileAnalysisStore.binary(
          file = analysisFile.toFile,
          mappers = mappers,
          reproducible = reproducible,
          parallelism = parallelism,
        )
      case (true, false) =>
        FileAnalysisStore.text(analysisFile.toFile, mappers)
      case (true, true) =>
        ConsistentFileAnalysisStore.text(
          file = analysisFile.toFile,
          mappers = mappers,
          reproducible = reproducible,
          parallelism = parallelism,
        )
    }
    val cachedStore = AnalysisStore.getCachedStore(fileStore)
    staticCache(analysisFile, AnalysisStore.getThreadSafeStore(cachedStore))
  }
}
