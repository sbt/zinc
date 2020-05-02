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
    val conv = converter.toOption.getOrElse(???)
    val defaultStampReader = Stamps.timeWrapLibraryStamps(conv)
    compileIncrementally(
      scalac,
      javacChosen,
      sources,
      classpath,
      CompileOutput(classesDirectory),
      earlyOutput.toOption,
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
      case e: CompileFailed        => throw e // just ignore
      case e: InterruptedException => throw e // just ignore
      case e: Throwable =>
        val ex = e // For Intellij debugging purpose
        val numberSources = s"$sourceCount sources"
        val outputString = output match {
          case singleOutput: SingleOutput =>
            singleOutput.getOutputDirectory().toString
          case multiOutput: MultipleOutput =>
            multiOutput
              .getOutputGroups()
              .map(_.getOutputDirectory().toString)
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
      incrementalOptions: IncOptions,
      temporaryClassesDirectory: Option[Path],
      extra: List[(String, String)],
      converter: FileConverter,
      stampReader: ReadStamps
  )(implicit logger: Logger): CompileResult = {
    handleCompilationError(sources.size, output, logger) {
      val prev = previousAnalysis match {
        case Some(previous) => previous
        case None           => Analysis.empty
      }
      val compileStraightToJar = JarUtils.isCompilingToJar(output)

      // otherwise jars on classpath will not be closed, especially prev jar.
      if (compileStraightToJar) sys.props.put("scala.classpath.closeZip", "true")

      val extraScalacOptions = {
        val scalaVersion = scalaCompiler.scalaInstance.version
        if (compileStraightToJar && scalaVersion.startsWith("2.12")) {
          JarUtils.scalacOptions
        } else Seq.empty
      }

      val extraJavacOptions = if (compileStraightToJar) {
        JarUtils.javacOptions
      } else Seq.empty

      val outputJarContent = JarUtils.createOutputJarContent(output)

      val config = MixedAnalyzingCompiler.makeConfig(
        scalaCompiler,
        javaCompiler,
        sources,
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
        stampReader,
        extra
      )
      if (skip) CompileResult.of(prev, config.currentSetup, false)
      else {
        JarUtils.setupTempClassesDir(temporaryClassesDirectory)
        val (analysis, changed) = compileInternal(
          MixedAnalyzingCompiler(config)(logger),
          equivCompileSetup(
            equivOpts0(equivScalacOptions(incrementalOptions.ignoredScalacOptions))
          ),
          equivPairs,
          logger
        )
        CompileResult.of(analysis, config.currentSetup, changed)
      }
    }
  }

  /**
   * Run the incremental compiler using the given mixed compiler.
   *
   * This operation prunes the inputs based on [[MiniSetup]].
   */
  private[sbt] def compileInternal(
      mixedCompiler: MixedAnalyzingCompiler,
      equiv: Equiv[MiniSetup],
      equivPairs: Equiv[Array[T2[String, String]]],
      log: Logger
  ): (Analysis, Boolean) = {
    import mixedCompiler.config._
    val lookup = new LookupImpl(mixedCompiler.config, previousSetup)
    val srcsSet = mixedCompiler.config.sources.toSet
    val analysis = previousSetup match {
      case Some(previous) =>
        // The dummy output needs to be changed to .jar for this to work again.
        //
        // if (compileToJarSwitchedOn(mixedCompiler.config)) {
        //   Analysis.empty
        // } else
        if (equiv.equiv(previous, currentSetup)) {
          previousAnalysis
          // Return an empty analysis if values of extra have changed
        } else if (!equivPairs.equiv(previous.extra, currentSetup.extra)) {
          Analysis.empty
        } else {
          Incremental.prune(srcsSet, previousAnalysis, output, outputJarContent, converter)
        }
      case None =>
        Incremental.prune(srcsSet, previousAnalysis, output, outputJarContent, converter)
    }

    // Run the incremental compilation
    val compile = Incremental(
      srcsSet,
      converter,
      lookup,
      analysis,
      incOptions,
      currentSetup,
      stampReader,
      output,
      outputJarContent,
      earlyOutput,
      log
    )(mixedCompiler.compile)
    compile.swap
  }

  // private def compileToJarSwitchedOn(config: CompileConfiguration): Boolean = {
  //   def isCompilingToJar = JarUtils.isCompilingToJar(config.output)
  //   def previousCompilationWasToJar = config.previousAnalysis match {
  //     case analysis: Analysis =>
  //       analysis.relations.allProducts.headOption match {
  //         case Some(product) => JarUtils.isClassInJar(product)
  //         case None          => true // we can assume it was, as it doesn't matter if there were no products
  //       }
  //     case _ => true
  //   }
  //
  //   isCompilingToJar && !previousCompilationWasToJar
  // }

  def setup(
      lookup: PerClasspathEntryLookup,
      skip: Boolean,
      cacheFile: Path,
      cache: GlobalsCache,
      incOptions: IncOptions,
      reporter: Reporter,
      optionProgress: Option[CompileProgress],
      extra: Array[T2[String, String]]
  ): Setup = {
    val progress = optionProgress.toOptional
    Setup.of(lookup, skip, cacheFile, cache, incOptions, reporter, progress, extra)
  }

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

  def previousResult(result: CompileResult): PreviousResult = {
    PreviousResult.of(
      Optional.of[CompileAnalysis](result.analysis),
      Optional.of[MiniSetup](result.setup)
    )
  }

  def emptyPreviousResult: PreviousResult = {
    PreviousResult.of(
      Optional.empty[CompileAnalysis],
      Optional.empty[MiniSetup]
    )
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
