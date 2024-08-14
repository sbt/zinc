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

import java.util.Optional
import java.util.function.{ Function => JavaFunction }
import java.nio.file.Path

import sbt.internal.inc.JavaInterfaceUtil._
import sbt.internal.inc.MiniSetupUtil._
import sbt.util.InterfaceUtil
import xsbti._
import xsbti.compile.CompileOrder.Mixed
import xsbti.compile.{ ClasspathOptions => XClasspathOptions, JavaTools => XJavaTools, _ }
import xsbti.compile.analysis.ReadStamps

class IncrementalCompilerImpl extends IncrementalCompiler {

  /**
   * Compile all Java sources based on xsbti.compile.Inputs.
   *
   * This is a Scala implementation of xsbti.compile.IncrementalCompiler,
   * check the docs for more information on the specification of this method.
   *
   * @param in An instance of xsbti.compile.Inputs that collect all the
   *           inputs required to run the compiler (from sources and classpath,
   *           to compilation order, previous results, current setup, etc).
   * @param logger An instance of `xsbti.Logger` to log Zinc output.
   *
   * @return An instance of `xsbti.compile.CompileResult` that holds
   *         information about the results of the compilation. The returned
   *         `xsbti.compile.CompileResult` must be used for subsequent
   *         compilations that depend on the same inputs, check its api and its
   *         field `xsbti.compile.CompileAnalysis`.
   */
  def compileAllJava(in: Inputs, logger: Logger): CompileResult = {
    val config = in.options()
    val setup = in.setup()
    import config._
    import setup._
    val compilers = in.compilers
    val javacChosen = compilers.javaTools.javac
    val scalac = compilers.scalac
    val extraOptions = extra.toList.map(_.toScalaTuple)
    val conv = converter.toOption.getOrElse(MappedFileConverter.empty)
    val defaultStampReader = Stamps.timeWrapBinaryStamps(conv)
    compileIncrementally(
      scalac,
      javacChosen,
      sources,
      classpath,
      CompileOutput(classesDirectory),
      earlyOutput.toOption,
      earlyAnalysisStore.toOption,
      cache,
      progress().toOption,
      scalacOptions,
      javacOptions,
      in.previousResult.analysis.toOption,
      in.previousResult.setup.toOption,
      perClasspathEntryLookup,
      reporter,
      order,
      skip = false,
      recompileAllJava = true,
      incrementalCompilerOptions,
      temporaryClassesDirectory.toOption,
      extraOptions,
      conv,
      stamper.toOption.getOrElse(defaultStampReader),
    )(logger)
  }

  /**
   * Performs an incremental compilation based on xsbti.compile.Inputs.
   *
   * This is a Scala implementation of xsbti.compile.IncrementalCompiler,
   * check the docs for more information on the specification of this method.
   *
   * @param in An instance of xsbti.compile.Inputs that collect all the
   *           inputs required to run the compiler (from sources and classpath,
   *           to compilation order, previous results, current setup, etc).
   * @param logger An instance of `xsbti.Logger` to log Zinc output.
   *
   * @return An instance of `xsbti.compile.CompileResult` that holds
   *         information about the results of the compilation. The returned
   *         `xsbti.compile.CompileResult` must be used for subsequent
   *         compilations that depend on the same inputs, check its api and its
   *         field `xsbti.compile.CompileAnalysis`.
   */
  override def compile(in: Inputs, logger: Logger): CompileResult = {
    val config = in.options()
    val setup = in.setup()
    import config._
    import setup._
    val compilers = in.compilers
    val javacChosen = compilers.javaTools.javac
    val scalac = compilers.scalac
    val extraOptions = extra.toList.map(_.toScalaTuple)
    val conv = converter.toOption.getOrElse(MappedFileConverter.empty)
    val defaultStampReader = Stamps.timeWrapBinaryStamps(conv)
    compileIncrementally(
      scalac,
      javacChosen,
      sources,
      classpath,
      CompileOutput(classesDirectory),
      earlyOutput.toOption,
      earlyAnalysisStore.toOption,
      cache,
      progress().toOption,
      scalacOptions,
      javacOptions,
      in.previousResult.analysis.toOption,
      in.previousResult.setup.toOption,
      perClasspathEntryLookup,
      reporter,
      order,
      skip,
      recompileAllJava = false,
      incrementalCompilerOptions,
      temporaryClassesDirectory.toOption,
      extraOptions,
      conv,
      stamper.toOption.getOrElse(defaultStampReader),
    )(logger)
  }

  /**
   *
   * Performs an incremental compilation based on xsbti.compile.Inputs.
   *
   * This is a Scala implementation of xsbti.compile.IncrementalCompiler,
   * check the docs for more information on the specification of this method.
   *
   * @param scalaCompiler The Scala compiler to compile Scala sources.
   * @param javaCompiler The Java compiler to compile Java sources.
   * @param sources An array of Java and Scala source files to be compiled.
   * @param classpath An array of files representing classpath entries.
   * @param output An instance of `Output` to store the compiler outputs.
   * @param cache                   Directory where previous cached compilers are stored.
   * @param scalaOptions            An array of options/settings for the Scala compiler.
   * @param javaOptions             An array of options for the Java compiler.
   * @param previousAnalysis        Optional previous incremental compilation analysis.
   * @param previousSetup           Optional previous incremental compilation setup.
   * @param perClasspathEntryLookup Lookup of data structures and operations
   *                                for a given classpath entry.
   * @param reporter                An instance of `Reporter` to report compiler output.
   * @param compileOrder            The order in which Java and Scala sources should
   *                                be compiled.
   * @param skip                    Flag to ignore this compilation run and return previous one.
   * @param progress                An instance of `CompileProgress` to keep track of
   *                                the current compilation progress.
   * @param incrementalOptions      An Instance of `IncOptions` that configures
   *                                the incremental compiler behaviour.
   * @param temporaryClassesDirectory A directory where incremental compiler
   *                                  will put temporary class files or jars.
   * @param extra                   An array of sbt tuples with extra options.
   * @param logger An instance of `Logger` that logs Zinc output.
   * @return An instance of `xsbti.compile.CompileResult` that holds
   *         information about the results of the compilation. The returned
   *         `xsbti.compile.CompileResult` must be used for subsequent
   *         compilations that depend on the same inputs, check its api and its
   *         field `xsbti.compile.CompileAnalysis`.
   */
  override def compile(
      scalaCompiler: xsbti.compile.ScalaCompiler,
      javaCompiler: xsbti.compile.JavaCompiler,
      sources: Array[VirtualFile],
      classpath: Array[VirtualFile],
      output: xsbti.compile.Output,
      earlyOutput: Optional[xsbti.compile.Output],
      earlyAnalysisStore: Optional[AnalysisStore],
      cache: xsbti.compile.GlobalsCache,
      scalaOptions: Array[String],
      javaOptions: Array[String],
      previousAnalysis: Optional[xsbti.compile.CompileAnalysis],
      previousSetup: Optional[xsbti.compile.MiniSetup],
      perClasspathEntryLookup: xsbti.compile.PerClasspathEntryLookup,
      reporter: Reporter,
      compileOrder: xsbti.compile.CompileOrder,
      skip: java.lang.Boolean,
      progress: Optional[xsbti.compile.CompileProgress],
      incrementalOptions: xsbti.compile.IncOptions,
      temporaryClassesDirectory: Optional[Path],
      extra: Array[xsbti.T2[String, String]],
      converter: FileConverter,
      stampReader: ReadStamps,
      logger: xsbti.Logger,
  ) = {
    val extraInScala = extra.toList.map(_.toScalaTuple)
    compileIncrementally(
      scalaCompiler,
      javaCompiler,
      sources.toVector,
      classpath.toSeq,
      output,
      earlyOutput.toOption,
      earlyAnalysisStore.toOption,
      cache,
      progress.toOption,
      scalaOptions.toSeq,
      javaOptions.toSeq,
      previousAnalysis.toOption,
      previousSetup.toOption,
      perClasspathEntryLookup,
      reporter,
      compileOrder,
      skip: Boolean,
      recompileAllJava = false,
      incrementalOptions,
      temporaryClassesDirectory.toOption,
      extraInScala,
      converter,
      stampReader
    )(logger)
  }

  /**
   *
   * Performs an incremental compilation based on xsbti.compile.Inputs.
   *
   * This is a Scala implementation of xsbti.compile.IncrementalCompiler,
   * check the docs for more information on the specification of this method.
   *
   * @param scalaCompiler The Scala compiler to compile Scala sources.
   * @param javaCompiler The Java compiler to compile Java sources.
   * @param sources An array of Java and Scala source files to be compiled.
   * @param classpath An array of files representing classpath entries.
   * @param output An instance of `Output` to store the compiler outputs.
   * @param cache                   Directory where previous cached compilers are stored.
   * @param scalaOptions            An array of options/settings for the Scala compiler.
   * @param javaOptions             An array of options for the Java compiler.
   * @param previousAnalysis        Optional previous incremental compilation analysis.
   * @param previousSetup           Optional previous incremental compilation setup.
   * @param perClasspathEntryLookup Lookup of data structures and operations
   *                                for a given classpath entry.
   * @param reporter                An instance of `Reporter` to report compiler output.
   * @param compileOrder            The order in which Java and Scala sources should
   *                                be compiled.
   * @param skip                    Flag to ignore this compilation run and return previous one.
   * @param progress                An instance of `CompileProgress` to keep track of
   *                                the current compilation progress.
   * @param incrementalOptions      An Instance of `IncOptions` that configures
   *                                the incremental compiler behaviour.
   * @param temporaryClassesDirectory A directory where incremental compiler
   *                                  will put temporary class files or jars.
   * @param extra                   An array of sbt tuples with extra options.
   * @param logger An instance of `Logger` that logs Zinc output.
   * @return An instance of `xsbti.compile.CompileResult` that holds
   *         information about the results of the compilation. The returned
   *         `xsbti.compile.CompileResult` must be used for subsequent
   *         compilations that depend on the same inputs, check its api and its
   *         field `xsbti.compile.CompileAnalysis`.
   */
  override def compile(
      scalaCompiler: xsbti.compile.ScalaCompiler,
      javaCompiler: xsbti.compile.JavaCompiler,
      sources: Array[Path],
      classpath: Array[Path],
      output: xsbti.compile.Output,
      earlyOutput: Optional[xsbti.compile.Output],
      earlyAnalysisStore: Optional[AnalysisStore],
      cache: xsbti.compile.GlobalsCache,
      scalaOptions: Array[String],
      javaOptions: Array[String],
      previousAnalysis: Optional[xsbti.compile.CompileAnalysis],
      previousSetup: Optional[xsbti.compile.MiniSetup],
      perClasspathEntryLookup: xsbti.compile.PerClasspathEntryLookup,
      reporter: Reporter,
      compileOrder: xsbti.compile.CompileOrder,
      skip: java.lang.Boolean,
      progress: Optional[xsbti.compile.CompileProgress],
      incrementalOptions: xsbti.compile.IncOptions,
      temporaryClassesDirectory: Optional[Path],
      extra: Array[xsbti.T2[String, String]],
      conveter: FileConverter,
      stampReader: ReadStamps,
      logger: xsbti.Logger,
  ) = {
    val extraInScala = extra.toList.map(_.toScalaTuple)
    val vs = sources.map(conveter.toVirtualFile(_))
    val cp = classpath.toSeq.map(conveter.toVirtualFile(_))
    compileIncrementally(
      scalaCompiler,
      javaCompiler,
      vs,
      cp,
      output,
      earlyOutput.toOption,
      earlyAnalysisStore.toOption,
      cache,
      progress.toOption,
      scalaOptions.toSeq,
      javaOptions.toSeq,
      previousAnalysis.toOption,
      previousSetup.toOption,
      perClasspathEntryLookup,
      reporter,
      compileOrder,
      skip: Boolean,
      recompileAllJava = false,
      incrementalOptions,
      temporaryClassesDirectory.toOption,
      extraInScala,
      conveter,
      stampReader
    )(logger)
  }

  /**
   * Handle the compilation error in an independent method to avoid
   * confusing compilation execution logic with error handling logic.
   */
  private def handleCompilationError(
      sourceCount: Int,
      output: Output,
      logger: Logger
  )(compilerRun: => CompileResult): CompileResult = {
    try {
      compilerRun
    } catch {
      case e: xsbti.CompileFailed =>
        throw new sbt.internal.inc.CompileFailed(
          e.arguments,
          e.toString,
          e.problems,
          e
        ) // just ignore
      case e: CompileFailed        => throw e // just ignore
      case e: InterruptedException => throw e // just ignore
      case e: Throwable =>
        val ex = e // For Intellij debugging purpose
        val numberSources = s"$sourceCount sources"
        val outputString = output match {
          case singleOutput: SingleOutput =>
            singleOutput.getOutputDirectoryAsPath().toString
          case multiOutput: MultipleOutput =>
            multiOutput
              .getOutputGroups()
              .map(_.getOutputDirectoryAsPath().toString)
              .mkString("[", ", ", "]")
          case _ =>
            s"other output ($output)"
        }

        val msg =
          s"""## Exception when compiling $numberSources to $outputString
             |${e.toString}
             |${ex.getStackTrace.mkString("\n")}
           """
        logger.error(InterfaceUtil.toSupplier(msg.stripMargin))
        throw ex
    }
  }

  /**
   * Run the mixed compilation of Java and Scala sources. This is the
   * actual implementation of the `compile` methods and they proxy to it.
   *
   * @param scalaCompiler The Scala compiler to compile Scala sources.
   * @param javaCompiler The Java compiler to compile Java sources.
   * @param sources An array of Java and Scala source files to be compiled.
   * @param classpath An array of files representing classpath entries.
   * @param output An instance of [[Output]] to store the compiler outputs.
   * @param cache                   Directory where previous cached compilers are stored.
   * @param scalaOptions            An array of options/settings for the Scala compiler.
   * @param javaOptions             An array of options for the Java compiler.
   * @param previousAnalysis        Optional previous incremental compilation analysis.
   * @param previousSetup           Optional previous incremental compilation setup.
   * @param perClasspathEntryLookup Lookup of data structures and operations
   *                                for a given classpath entry.
   * @param reporter                An instance of [[Reporter]] to report compiler output.
   * @param compileOrder            The order in which Java and Scala sources should
   *                                be compiled.
   * @param skip                    Flag to ignore this compilation run and return previous one.
   * @param progress                An instance of [[CompileProgress]] to keep track of
   *                                the current compilation progress.
   * @param incrementalOptions      An Instance of [[IncOptions]] that configures
   *                                the incremental compiler behaviour.
   * @param extra                   An array of sbt tuples with extra options.
   * @param logger An instance of [[Logger]] that logs Zinc output.
   * @return An instance of [[xsbti.compile.CompileResult]] that holds
   *         information about the results of the compilation. The returned
   *         [[xsbti.compile.CompileResult]] must be used for subsequent
   *         compilations that depend on the same inputs, check its api and its
   *         field [[xsbti.compile.CompileAnalysis]].
   */
  private[sbt] def compileIncrementally(
      scalaCompiler: xsbti.compile.ScalaCompiler,
      javaCompiler: xsbti.compile.JavaCompiler,
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      output: Output,
      earlyOutput: Option[Output],
      earlyAnalysisStore: Option[AnalysisStore],
      cache: GlobalsCache,
      progress: Option[CompileProgress] = None,
      scalaOptions: Seq[String] = Nil,
      javaOptions: Seq[String] = Nil,
      previousAnalysis: Option[CompileAnalysis],
      previousSetup: Option[MiniSetup],
      perClasspathEntryLookup: PerClasspathEntryLookup,
      reporter: Reporter,
      compileOrder: CompileOrder = Mixed,
      skip: Boolean = false,
      recompileAllJava: Boolean = false,
      incrementalOptions: IncOptions,
      temporaryClassesDirectory: Option[Path],
      extra: List[(String, String)],
      converter: FileConverter,
      stampReader: ReadStamps
  )(implicit logger: Logger): CompileResult = {
    handleCompilationError(sources.size, output, logger) {
      val prev = previousAnalysis match {
        case Some(previous) => previous.asInstanceOf[Analysis]
        case None           => Analysis.empty
      }
      val javaSrcs = sources.filter(MixedAnalyzingCompiler.javaOnly)
      val outputs = output :: earlyOutput.toList
      val outputJars = outputs.flatMap(out => (JarUtils.getOutputJar(out): Option[Path]).toList)
      val classpathPaths = classpath.map(converter.toPath)
      val outputJarsOnCp = outputJars.exists { outputJar =>
        classpathPaths.exists { x: Path =>
          x.toAbsolutePath == outputJar.toAbsolutePath
        }
      }

      // otherwise jars on classpath will not be closed, especially prev jar.
      if (outputJarsOnCp) sys.props.put("scala.classpath.closeZip", "true")

      val extraScalacOptions = {
        if (outputJarsOnCp && Set("2.12", "2.13")(scalaCompiler.scalaInstance.version.take(4)))
          JarUtils.scalacOptions
        else Nil
      }

      val extraJavacOptions = if (outputJarsOnCp) JarUtils.javacOptions else Nil

      val outputJarContent = JarUtils.createOutputJarContent(output)

      val config = MixedAnalyzingCompiler.makeConfig(
        scalaCompiler,
        javaCompiler,
        if (recompileAllJava) javaSrcs
        else sources,
        converter,
        classpath,
        cache,
        progress,
        scalaOptions ++ extraScalacOptions,
        javaOptions ++ extraJavacOptions,
        prev,
        previousSetup,
        perClasspathEntryLookup,
        reporter,
        compileOrder,
        skip,
        incrementalOptions,
        output,
        outputJarContent,
        earlyOutput,
        earlyAnalysisStore,
        stampReader,
        extra
      )
      import config.currentSetup

      if (skip && earlyOutput.isEmpty || recompileAllJava && javaSrcs.isEmpty) {
        CompileResult.of(prev, currentSetup, false)
      } else {
        JarUtils.setupTempClassesDir(temporaryClassesDirectory)
        val (changed, analysis) =
          if (recompileAllJava) {
            compileAllJava(MixedAnalyzingCompiler(config)(logger))
          } else {
            compileInternal(MixedAnalyzingCompiler(config)(logger))
          }
        CompileResult.of(analysis, currentSetup, changed)
      }
    }
  }

  /** Compile all Java sources using the given mixed compiler. */
  private[sbt] def compileAllJava(mixedCompiler: MixedAnalyzingCompiler): (Boolean, Analysis) = {
    import mixedCompiler._, config._, currentSetup._
    val lookup = new LookupImpl(mixedCompiler.config, previousSetup)
    Incremental.compileAllJava(
      sources,
      converter,
      lookup,
      prevAnalysis(mixedCompiler),
      incOptions,
      currentSetup,
      stampReader,
      output,
      outputJarContent,
      earlyOutput,
      earlyAnalysisStore,
      progress,
      log
    )(mixedCompiler.compileJava)
  }

  /** Run the incremental compiler using the given mixed compiler. */
  private[sbt] def compileInternal(mixedCompiler: MixedAnalyzingCompiler): (Boolean, Analysis) = {
    import mixedCompiler._, config._, currentSetup._
    val lookup = new LookupImpl(mixedCompiler.config, previousSetup)
    Incremental.apply(
      sources.toSet,
      converter,
      lookup,
      prevAnalysis(mixedCompiler),
      incOptions,
      currentSetup,
      stampReader,
      output,
      outputJarContent,
      earlyOutput,
      earlyAnalysisStore,
      progress,
      log
    )(mixedCompiler.compile)
  }

  private def prevAnalysis(mixedCompiler: MixedAnalyzingCompiler) = {
    import mixedCompiler._, config._, currentSetup._
    val equivOpts = equivOpts0(equivScalacOptions(incOptions.ignoredScalacOptions))
    val equiv = equivCompileSetup(mixedCompiler.log, equivOpts)
    previousSetup match {
      // The dummy output needs to be changed to .jar for this to work again.
      case _ if compileToJarSwitchedOn(config)           => Analysis.empty
      case Some(prev) if equiv.equiv(prev, currentSetup) => previousAnalysis
      case Some(prev) if !equivPairs.equiv(prev.extra, currentSetup.extra) =>
        import sbt.internal.inc.ClassFileManager
        val classFileManager =
          ClassFileManager.getClassFileManager(incOptions, output, outputJarContent)
        val products = previousAnalysis.asInstanceOf[Analysis].relations.allProducts
        classFileManager.delete(products.map(converter.toVirtualFile).toArray)
        Analysis.empty
      case _ =>
        val srcs = config.sources.toSet
        Incremental.prune(srcs, previousAnalysis, output, outputJarContent, converter, incOptions)
    }
  }

  private def compileToJarSwitchedOn(config: CompileConfiguration): Boolean = {
    def isCompilingToJar = JarUtils.isCompilingToJar(config.currentSetup.output)
    def previousCompilationWasToJar =
      config.previousSetup.exists(s => JarUtils.isCompilingToJar(s.output))
    isCompilingToJar && !previousCompilationWasToJar
  }

  def setup(
      lookup: PerClasspathEntryLookup,
      skip: Boolean,
      cacheFile: Path,
      cache: GlobalsCache,
      incOptions: IncOptions,
      reporter: Reporter,
      progress: Option[CompileProgress],
      earlyAnalysisStore: Option[AnalysisStore],
      extra: Array[T2[String, String]]
  ): Setup =
    Setup.of(
      lookup,
      skip,
      cacheFile,
      cache,
      incOptions,
      reporter,
      progress.toOptional,
      earlyAnalysisStore.toOptional,
      extra
    )

  def inputs(
      options: CompileOptions,
      compilers: Compilers,
      setup: Setup,
      pr: PreviousResult
  ): Inputs = {
    Inputs.of(compilers, options, setup, pr)
  }

  def inputs(
      classpath: Array[VirtualFile],
      sources: Array[VirtualFile],
      classesDirectory: Path,
      earlyJarPath: Option[Path],
      scalacOptions: Array[String],
      javacOptions: Array[String],
      maxErrors: Int,
      sourcePositionMappers: Array[JavaFunction[Position, Optional[Position]]],
      order: CompileOrder,
      compilers: Compilers,
      setup: Setup,
      pr: PreviousResult,
      temporaryClassesDirectory: Optional[Path],
      converter: FileConverter,
      stampReader: ReadStamps
  ): Inputs = {
    val compileOptions = {
      CompileOptions.of(
        classpath,
        sources,
        classesDirectory,
        scalacOptions,
        javacOptions,
        maxErrors,
        foldMappers(sourcePositionMappers),
        order,
        temporaryClassesDirectory,
        Option(converter).toOptional,
        Option(stampReader).toOptional,
        (earlyJarPath map { CompileOutput(_) }).toOptional,
      )
    }
    inputs(compileOptions, compilers, setup, pr)
  }

  def previousResult(contents: AnalysisContents): PreviousResult = {
    PreviousResult.of(Optional.of(contents.getAnalysis), Optional.of(contents.getMiniSetup))
  }

  def emptyPreviousResult: PreviousResult = {
    PreviousResult.of(Optional.empty[CompileAnalysis], Optional.empty[MiniSetup])
  }

  def compilers(
      instance: xsbti.compile.ScalaInstance,
      cpOptions: XClasspathOptions,
      javaHome: Option[Path],
      scalac: ScalaCompiler
  ): Compilers =
    ZincUtil.compilers(instance, cpOptions, javaHome, scalac)

  def compilers(javaTools: XJavaTools, scalac: ScalaCompiler): Compilers =
    ZincUtil.compilers(javaTools, scalac)

  /* *********************************************************************** */
  /* * Define helpers to convert from sbt Java interface to the Scala one  * */
  /* *********************************************************************** */

  private[sbt] def foldMappers[A](mappers: Array[JavaFunction[A, Optional[A]]]) = {
    mappers.foldRight(InterfaceUtil.toJavaFunction[A, A](identity)) { (mapper, mappers) =>
      InterfaceUtil.toJavaFunction[A, A]({ p: A =>
        mapper(p).toOption.getOrElse(mappers(p))
      })
    }
  }
}
