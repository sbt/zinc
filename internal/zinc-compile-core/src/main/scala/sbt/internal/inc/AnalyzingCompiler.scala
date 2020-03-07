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

import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.net.URLClassLoader
import java.util.Optional

import sbt.util.Logger
import sbt.io.syntax._
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.util.ManagedLogger
import xsbti.{
  AnalysisCallback,
  PathBasedFile,
  Reporter,
  ReporterUtil,
  Logger => xLogger,
  VirtualFile
}
import xsbti.compile._

/**
 * Implement a cached incremental `ScalaCompiler` that has been instrumented
 * with the dependency analysis plugin to do incremental compilation.
 *
 * @param scalaInstance The Scala instance to be used for the compiler.
 * @param provider The manager and provider of cached compilers.
 * @param classpathOptions The classpath options for the Scala compiler.
 * @param onArgsHandler Handler that will handle arguments.
 * @param classLoaderCache
 */
final class AnalyzingCompiler(
    // This scala instance refers to the interface
    val scalaInstance: xsbti.compile.ScalaInstance,
    val provider: CompilerBridgeProvider,
    override val classpathOptions: ClasspathOptions,
    onArgsHandler: Seq[String] => Unit,
    val classLoaderCache: Option[ClassLoaderCache]
) extends CachedCompilerProvider
    with ScalaCompiler {

  def onArgs(f: Seq[String] => Unit): AnalyzingCompiler =
    new AnalyzingCompiler(scalaInstance, provider, classpathOptions, f, classLoaderCache)

  def withClassLoaderCache(classLoaderCache: ClassLoaderCache) =
    new AnalyzingCompiler(
      scalaInstance,
      provider,
      classpathOptions,
      onArgsHandler,
      Some(classLoaderCache)
    )

  def apply(
      sources: Array[VirtualFile],
      changes: DependencyChanges,
      classpath: Array[VirtualFile],
      singleOutput: Path,
      options: Array[String],
      callback: AnalysisCallback,
      maximumErrors: Int,
      cache: GlobalsCache,
      log: ManagedLogger
  ): Unit = {
    val compArgs = new CompilerArguments(scalaInstance, classpathOptions)
    val arguments = compArgs.makeArguments(Nil, classpath, options)
    val output = CompileOutput(singleOutput)
    val basicReporterConfig = ReporterUtil.getDefaultReporterConfig()
    val reporterConfig = basicReporterConfig.withMaximumErrors(maximumErrors)
    val reporter = ReporterManager.getReporter(log, reporterConfig)
    val progress = Optional.empty[CompileProgress]
    compile(sources, changes, arguments.toArray, output, callback, reporter, cache, log, progress)
  }

  def compile(
      sources: Array[VirtualFile],
      changes: DependencyChanges,
      options: Array[String],
      output: Output,
      callback: AnalysisCallback,
      reporter: Reporter,
      cache: GlobalsCache,
      log: xLogger,
      progressOpt: Optional[CompileProgress]
  ): Unit = {
    val cached = cache(options, output, !changes.isEmpty, this, log, reporter)
    try {
      val progress = if (progressOpt.isPresent) progressOpt.get else IgnoreProgress
      compile(sources, changes, callback, log, reporter, progress, cached)
    } finally {
      cached match {
        case c: java.io.Closeable => c.close()
        case _                    =>
      }
    }
  }

  def compile(
      sources: Array[VirtualFile],
      changes: DependencyChanges,
      callback: AnalysisCallback,
      log: xLogger,
      reporter: Reporter,
      progress: CompileProgress,
      compiler: CachedCompiler
  ): Unit = {
    // onArgsHandler(compiler.commandArguments(sources))
    call("xsbt.CompilerInterface", "run", log)(
      classOf[Array[VirtualFile]],
      classOf[DependencyChanges],
      classOf[AnalysisCallback],
      classOf[xLogger],
      classOf[Reporter],
      classOf[CompileProgress],
      classOf[CachedCompiler]
    )(sources, changes, callback, log, reporter, progress, compiler)
    ()
  }

  def newCachedCompiler(
      arguments: Array[String],
      output: Output,
      log: xLogger,
      reporter: Reporter
  ): CachedCompiler =
    newCachedCompiler(arguments: Seq[String], output, log, reporter)

  def newCachedCompiler(
      arguments: Seq[String],
      output: Output,
      log: xLogger,
      reporter: Reporter
  ): CachedCompiler = {
    val compiler = call("xsbt.CompilerInterface", "newCompiler", log)(
      classOf[Array[String]],
      classOf[Output],
      classOf[xLogger],
      classOf[Reporter]
    )(arguments.toArray[String], output, log, reporter)
    compiler.asInstanceOf[CachedCompiler]
  }

  def doc(
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      outputDirectory: Path,
      options: Seq[String],
      maximumErrors: Int,
      log: ManagedLogger
  ): Unit = {
    val reporter = new ManagedLoggedReporter(maximumErrors, log)
    doc(sources, classpath, outputDirectory, options, log, reporter)
  }

  def doc(
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      outputDirectory: Path,
      options: Seq[String],
      log: Logger,
      reporter: Reporter
  ): Unit = {
    val compArgs = new CompilerArguments(scalaInstance, classpathOptions)
    val arguments =
      compArgs.makeArguments(Nil, classpath, Some(outputDirectory), options)
    onArgsHandler(arguments)
    call("xsbt.ScaladocInterface", "run", log)(
      classOf[Array[VirtualFile]],
      classOf[Array[String]],
      classOf[xLogger],
      classOf[Reporter]
    )(sources.toArray, arguments.toArray[String], log, reporter)
    ()
  }

  def console(
      classpath: Seq[VirtualFile],
      options: Seq[String],
      initialCommands: String,
      cleanupCommands: String,
      log: Logger
  )(loader: Option[ClassLoader] = None, bindings: Seq[(String, Any)] = Nil): Unit = {
    onArgsHandler(consoleCommandArguments(classpath, options, log))
    val (classpathString, bootClasspath) = consoleClasspaths(classpath)
    val (names, values) = bindings.unzip
    call("xsbt.ConsoleInterface", "run", log)(
      classOf[Array[String]],
      classOf[String],
      classOf[String],
      classOf[String],
      classOf[String],
      classOf[ClassLoader],
      classOf[Array[String]],
      classOf[Array[Any]],
      classOf[xLogger]
    )(
      options.toArray[String]: Array[String],
      bootClasspath,
      classpathString,
      initialCommands,
      cleanupCommands,
      loader.orNull,
      names.toArray[String],
      values.toArray[Any],
      log
    )
    ()
  }

  private[this] def consoleClasspaths(classpath: Seq[VirtualFile]): (String, String) = {
    val arguments = new CompilerArguments(scalaInstance, classpathOptions)
    val cp = classpath map {
      case x: PathBasedFile => x.toPath
    }
    val classpathString = CompilerArguments.absString(arguments.finishClasspath(cp))
    val bootClasspath =
      if (classpathOptions.autoBoot) arguments.createBootClasspathFor(cp) else ""
    (classpathString, bootClasspath)
  }

  def consoleCommandArguments(
      classpath: Seq[VirtualFile],
      options: Seq[String],
      log: Logger
  ): Seq[String] = {
    val (classpathString, bootClasspath) = consoleClasspaths(classpath)
    val argsObj = call("xsbt.ConsoleInterface", "commandArguments", log)(
      classOf[Array[String]],
      classOf[String],
      classOf[String],
      classOf[xLogger]
    )(options.toArray[String], bootClasspath, classpathString, log)
    argsObj.asInstanceOf[Array[String]].toSeq
  }

  def force(log: Logger): Unit = { provider.fetchCompiledBridge(scalaInstance, log); () }

  private def call(
      interfaceClassName: String,
      methodName: String,
      log: Logger
  )(argTypes: Class[_]*)(args: AnyRef*): AnyRef = {
    val interfaceClass = getInterfaceClass(interfaceClassName, log)
    val interface = interfaceClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
    val method = interfaceClass.getMethod(methodName, argTypes: _*)
    try method.invoke(interface, args: _*)
    catch {
      case e: InvocationTargetException =>
        e.getCause match {
          case c: xsbti.CompileFailed =>
            throw new CompileFailed(c.arguments, c.toString, c.problems)
          case t => throw t
        }
    }
  }

  private[this] def loader(log: Logger) = {
    val interfaceJar = provider.fetchCompiledBridge(scalaInstance, log)
    def createInterfaceLoader =
      new URLClassLoader(
        Array(interfaceJar.toURI.toURL),
        createDualLoader(scalaInstance.loader(), getClass.getClassLoader)
      )

    classLoaderCache match {
      case Some(cache) =>
        cache.cachedCustomClassloader(
          interfaceJar :: scalaInstance.allJars().toList,
          () => createInterfaceLoader
        )
      case None => createInterfaceLoader
    }
  }

  private[this] def getInterfaceClass(name: String, log: Logger) =
    Class.forName(name, true, loader(log))

  protected def createDualLoader(
      scalaLoader: ClassLoader,
      sbtLoader: ClassLoader
  ): ClassLoader = {
    val xsbtiFilter = (name: String) => name.startsWith("xsbti.")
    val notXsbtiFilter = (name: String) => !xsbtiFilter(name)
    new classpath.DualLoader(
      scalaLoader,
      notXsbtiFilter,
      _ => true,
      sbtLoader,
      xsbtiFilter,
      _ => false
    )
  }

  override def toString = s"Analyzing compiler (Scala ${scalaInstance.actualVersion})"
}

object AnalyzingCompiler {
  import sbt.io.IO.{ copy, zip, unzip, withTemporaryDirectory }

  /**
   * Compile a Scala bridge from the sources of the compiler as follows:
   *   1. Extract sources from source jars.
   *   2. Compile them with the `xsbti` interfaces on the classpath.
   *   3. Package the compiled classes and generated resources into a JAR.
   *
   * The Scala build depends on some details of this method, please check
   * <a href="https://github.com/jsuereth/scala/commit/3431860048df8d2a381fb85a526097e00154eae0">
   * this link for more information</a>.
   *
   * This method is invoked by build tools to compile the compiler bridge
   * for Scala versions in which they are not present (e.g. every time a new
   * Scala version is installed in your system).
   */
  def compileSources(
      sourceJars: Iterable[Path],
      targetJar: Path,
      xsbtiJars: Iterable[Path],
      id: String,
      compiler: RawCompiler,
      log: Logger
  ): Unit = {
    val isSource = (f: Path) => isSourceName(f.getFileName.toString)
    def keepIfSource(files: Set[Path]): Set[Path] =
      if (files.exists(isSource)) files else Set.empty

    // Generate jar from compilation dirs, the resources and a target name.
    def generateJar(outputDir: File, dir: File, resources: Seq[File], targetJar: File) = {
      import sbt.io.Path._
      copy(resources.pair(rebase(dir, outputDir)))
      val toBeZipped = outputDir.allPaths.pair(relativeTo(outputDir), errorIfNone = false)
      zip(toBeZipped, targetJar, Some(0L))
    }

    // Handle the compilation failure of the Scala compiler.
    def handleCompilationError(compilation: => Unit) = {
      try compilation
      catch {
        case e: xsbti.CompileFailed =>
          val msg = s"Error compiling the sbt component '$id'"
          throw new CompileFailed(e.arguments, msg, e.problems)
      }
    }

    withTemporaryDirectory { dir =>
      // Extract the sources to be compiled
      val extractedSources: Seq[Path] = sourceJars
        .foldLeft(Set.empty[Path]) { (extracted, sourceJar) =>
          extracted ++ keepIfSource(unzip(sourceJar.toFile, dir).map(_.toPath))
        }
        .toSeq
      val (sourceFiles, resources) = extractedSources.partition(isSource)
      withTemporaryDirectory { outputDirectory =>
        val scalaVersion = compiler.scalaInstance.actualVersion
        val msg = s"Non-compiled module '$id' for Scala $scalaVersion. Compiling..."
        log.info(msg)
        val start = System.currentTimeMillis
        handleCompilationError {
          val scalaLibraryJars = compiler.scalaInstance.libraryJars
          val restClasspath = xsbtiJars.toSeq ++ sourceJars
          val classpath = scalaLibraryJars.map(_.toPath) ++ restClasspath
          val vs = sourceFiles.map(PlainVirtualFile(_))
          val cp = classpath.map(PlainVirtualFile(_))
          compiler(vs, cp, outputDirectory.toPath, "-nowarn" :: Nil)

          val end = (System.currentTimeMillis - start) / 1000.0
          log.info(s"  Compilation completed in ${end}s.")
        }

        // Create a jar out of the generated class files
        generateJar(outputDirectory, dir, resources.map(_.toFile), targetJar.toFile)
      }
    }
  }

  private def isSourceName(name: String): Boolean =
    name.endsWith(".scala") || name.endsWith(".java")
}

private[this] object IgnoreProgress extends CompileProgress {
  override def startUnit(phase: String, unitPath: String): Unit = ()
  override def advance(current: Int, total: Int, prevPhase: String, nextPhase: String) = true
  override def earlyOutputComplete = ()
}
