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

import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.net.URLClassLoader
import java.util.{ Optional, ServiceLoader }

import sbt.util.{ InterfaceUtil, Logger }
import sbt.io.syntax._
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.util.ManagedLogger
import xsbti.{
  AnalysisCallback,
  FileConverter,
  InteractiveConsoleFactory,
  Reporter,
  Logger => xLogger,
  VirtualFile
}
import xsbti.compile._
import scala.language.existentials

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
) extends ScalaCompiler {

  /** Mechanism to work with compiler arguments. */
  private[this] val compArgs = new CompilerArguments(scalaInstance, classpathOptions)

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

  override def compile(
      sources: Array[VirtualFile],
      classpath: Array[VirtualFile],
      converter: FileConverter,
      changes: DependencyChanges,
      options: Array[String],
      output: Output,
      callback: AnalysisCallback,
      reporter: Reporter,
      progressOpt: Optional[CompileProgress],
      log: xLogger
  ): Unit = {
    val progress = if (progressOpt.isPresent) progressOpt.get else IgnoreProgress
    val cp = classpath.map(converter.toPath).toIndexedSeq
    val arguments = compArgs.makeArguments(Nil, cp, options.toIndexedSeq).toArray
    // hold reference to compiler bridge class loader to prevent its being evicted
    // from the compiler cache (sbt/zinc#914)
    val loader = getCompilerLoader(log)

    loadService(classOf[CompilerInterface2], loader) match {
      case Some(intf) =>
        intf.run(sources, changes, arguments, output, callback, reporter, progress, log)
      case _ =>
        // fall back to old reflection if CompilerInterface2 is not supported
        val compilerBridgeClassName = "xsbt.CompilerInterface"
        val (bridge, bridgeClass) = bridgeInstance(compilerBridgeClassName, loader)
        val compiler = invoke(bridge, bridgeClass, "newCompiler", log)(
          classOf[Array[String]],
          classOf[Output],
          classOf[xLogger],
          classOf[Reporter]
        )(arguments, output, log, reporter).asInstanceOf[CachedCompiler]
        val fileSources: Array[File] = sources.map(converter.toPath(_).toFile)
        try {
          invoke(bridge, bridgeClass, "run", log)(
            classOf[Array[File]],
            classOf[DependencyChanges],
            classOf[AnalysisCallback],
            classOf[xLogger],
            classOf[Reporter],
            classOf[CompileProgress],
            classOf[CachedCompiler]
          )(fileSources, changes, callback, log, reporter, progress, compiler)
          ()
        } finally {
          compiler match {
            case c: java.io.Closeable => c.close()
            case _                    =>
          }
        }
    }
  }

  def doc(
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      converter: FileConverter,
      outputDirectory: Path,
      options: Seq[String],
      maximumErrors: Int,
      log: ManagedLogger
  ): Unit = {
    val reporter = new ManagedLoggedReporter(maximumErrors, log)
    doc(sources, classpath, converter, outputDirectory, options, log, reporter)
  }

  def doc(
      sources: Seq[VirtualFile],
      classpath: Seq[VirtualFile],
      converter: FileConverter,
      outputDirectory: Path,
      options: Seq[String],
      log: Logger,
      reporter: Reporter
  ): Unit = {
    val cp = classpath.map(converter.toPath)
    // hold reference to compiler bridge class loader to prevent its being evicted
    // from the compiler cache (sbt/zinc#914)
    val loader = getDocLoader(log)
    loadService(classOf[ScaladocInterface2], loader) match {
      case Some(intf) =>
        val arguments =
          compArgs.makeArguments(Nil, cp, Some(outputDirectory), options)
        onArgsHandler(arguments)
        intf.run(sources.toArray, arguments.toArray[String], log, reporter)
      case _ =>
        loadService(classOf[ScaladocInterface1], loader) match {
          case Some(intf) =>
            val fileSources: Seq[Path] = sources.map(converter.toPath(_))
            val arguments =
              compArgs.makeArguments(fileSources, cp, Some(outputDirectory), options)
            onArgsHandler(arguments)
            intf.run(arguments.toArray[String], log, reporter)
          case _ =>
            // fall back to old reflection
            val scaladocBridgeClassName = "xsbt.ScaladocInterface"
            val (bridge, bridgeClass) = bridgeInstance(scaladocBridgeClassName, loader)
            val fileSources: Seq[Path] = sources.map(converter.toPath(_))
            val arguments =
              compArgs.makeArguments(fileSources, cp, Some(outputDirectory), options)
            onArgsHandler(arguments)
            invoke(bridge, bridgeClass, "run", log)(
              classOf[Array[String]],
              classOf[xLogger],
              classOf[Reporter]
            )(arguments.toArray[String], log, reporter)
        }
    }
    ()
  }

  def console(
      classpath: Seq[VirtualFile],
      converter: FileConverter,
      options: Seq[String],
      initialCommands: String,
      cleanupCommands: String,
      log: Logger
  )(loader: Option[ClassLoader] = None, bindings: Seq[(String, Any)] = Nil): Unit = {
    onArgsHandler(consoleCommandArguments(classpath, converter, options, log))
    val (classpathString, bootClasspath) = consoleClasspaths(classpath, converter)
    val (names, values0) = bindings.unzip
    val values = values0.toArray[Any].asInstanceOf[Array[AnyRef]]
    // hold reference to compiler bridge class loader to prevent its being evicted
    // from the compiler cache (sbt/zinc#914)
    val classLoader = getCompilerLoader(log)

    loadService(classOf[ConsoleInterface1], classLoader) match {
      case Some(intf) =>
        intf.run(
          options.toArray[String]: Array[String],
          bootClasspath,
          classpathString,
          initialCommands,
          cleanupCommands,
          loader.orNull,
          names.toArray[String],
          values,
          log
        )
      case _ =>
        // fall back to old reflection if ConsoleInterface1 is not supported
        val consoleBridgeClassName = "xsbt.ConsoleInterface"
        val (bridge, bridgeClass) = bridgeInstance(consoleBridgeClassName, classLoader)
        invoke(bridge, bridgeClass, "run", log)(
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
    }
    ()
  }

  private[this] def consoleClasspaths(
      classpath: Seq[VirtualFile],
      converter: FileConverter
  ): (String, String) = {
    val cp = classpath map { converter.toPath }
    val classpathString = CompilerArguments.absString(compArgs.finishClasspath(cp))
    val bootClasspath =
      if (classpathOptions.autoBoot) compArgs.createBootClasspathFor(cp) else ""
    (classpathString, bootClasspath)
  }

  def consoleCommandArguments(
      classpath: Seq[VirtualFile],
      converter: FileConverter,
      options: Seq[String],
      log: Logger
  ): Seq[String] = {
    val (classpathString, bootClasspath) = consoleClasspaths(classpath, converter)
    // hold reference to compiler bridge class loader to prevent its being evicted
    // from the compiler cache (sbt/zinc#914)
    val loader = getCompilerLoader(log)
    val argsObj = loadService(classOf[ConsoleInterface1], loader) match {
      case Some(intf) =>
        intf.commandArguments(options.toArray[String], bootClasspath, classpathString, log)
      case _ =>
        val consoleBridgeClassName = "xsbt.ConsoleInterface"
        val (bridge, bridgeClass) = bridgeInstance(consoleBridgeClassName, loader)
        invoke(bridge, bridgeClass, "commandArguments", log)(
          classOf[Array[String]],
          classOf[String],
          classOf[String],
          classOf[xLogger]
        )(options.toArray[String], bootClasspath, classpathString, log)
    }
    argsObj.asInstanceOf[Array[String]].toSeq
  }

  def interactiveConsole(
      classpath: Seq[VirtualFile],
      converter: FileConverter,
      options: Seq[String],
      initialCommands: String,
      cleanupCommands: String,
      log: Logger
  )(
      loader: Option[ClassLoader] = None,
      bindings: Seq[(String, AnyRef)] = Nil
  ): xsbti.InteractiveConsoleInterface = {
    onArgsHandler(consoleCommandArguments(classpath, converter, options, log))
    val (classpathString, bootClasspath) = consoleClasspaths(classpath, converter)
    val (names, values0) = bindings.unzip
    val values = values0.toArray[Any].asInstanceOf[Array[AnyRef]]
    // hold reference to compiler bridge class loader to prevent its being evicted
    // from the compiler cache (sbt/zinc#914)
    val classLoader = getCompilerLoader(log)
    loadService(classOf[InteractiveConsoleFactory], classLoader) match {
      case Some(intf) =>
        intf.createConsole(
          options.toArray[String]: Array[String],
          bootClasspath,
          classpathString,
          initialCommands,
          cleanupCommands,
          InterfaceUtil.toOptional(loader),
          names.toArray[String],
          values,
          log
        )
      case _ =>
        sys.error(s"xsbti.InteractiveConsoleFactory service was not found")
    }
  }

  // see https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html
  private def loadService[A](cls: Class[A], loader: ClassLoader): Option[A] = {
    import scala.collection.JavaConverters._
    val sl = ServiceLoader.load(cls, loader)
    val list = sl.iterator.asScala.toList
    list.lastOption
  }

  private def bridgeInstance(bridgeClassName: String, loader: ClassLoader): (AnyRef, Class[?]) = {
    val bridgeClass = getBridgeClass(bridgeClassName, loader)
    (bridgeClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef], bridgeClass)
  }

  private def invoke(bridge: AnyRef, bridgeClass: Class[?], methodName: String, log: Logger)(
      argTypes: Class[?]*
  )(args: AnyRef*): AnyRef = {
    val method = bridgeClass.getMethod(methodName, argTypes: _*)
    try method.invoke(bridge, args: _*)
    catch {
      case e: InvocationTargetException =>
        e.getCause match {
          case c: xsbti.CompileFailed =>
            throw new CompileFailed(c.arguments, c.toString, c.problems)
          case t => throw t
        }
    }
  }

  private[this] def getCompilerLoader(log: Logger): ClassLoader = {
    // could crash if the `CompilerBridge` tries to load classes
    // that are not in `scalaInstance.compilerJars`
    getDualLoader(scalaInstance.compilerJars.toList, scalaInstance.loaderCompilerOnly, log)
  }

  private[this] def getDocLoader(log: Logger): ClassLoader =
    getDualLoader(scalaInstance.allJars.toList, scalaInstance.loader, log)

  private[this] def getDualLoader(
      scalaJars: List[File],
      scalaLoader: ClassLoader,
      log: Logger
  ): ClassLoader = {
    val compilerBridgeJar = provider.fetchCompiledBridge(scalaInstance, log)
    def createCompilerBridgeLoader =
      new URLClassLoader(
        Array(compilerBridgeJar.toURI.toURL),
        createDualLoader(scalaLoader, getClass.getClassLoader)
      )

    classLoaderCache match {
      case Some(cache) =>
        cache.cachedCustomClassloader(
          compilerBridgeJar :: scalaJars,
          () => createCompilerBridgeLoader
        )
      case None => createCompilerBridgeLoader
    }
  }

  private[this] def getBridgeClass(name: String, loader: ClassLoader) =
    Class.forName(name, true, loader)

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
          compiler(sourceFiles, classpath.toIndexedSeq, outputDirectory.toPath, "-nowarn" :: Nil)

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

private[this] object IgnoreProgress extends CompileProgress
