/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc
package javac

import java.io.File
import java.net.URLClassLoader

import sbt.internal.inc.classfile.Analyze
import sbt.internal.inc.classpath.ClasspathUtilities
import xsbti.compile._
import xsbti.{ AnalysisCallback, Reporter => XReporter, Logger => XLogger }
import sbt.io.PathFinder

import sbt.util.InterfaceUtil
import sbt.util.Logger

/**
 * Define a Java compiler that reports on any discovered source dependencies or
 * APIs found via the incremental compilation and [[AnalysisCallback]].
 *
 * Note that this compiler does not implement a [[CachedCompilerProvider]]
 * because the Java compiler can easily be initialized via reflection.
 *
 * @param javac An instance of a Java compiler.
 * @param classpath The classpath to be used by Javac.
 * @param scalaInstance The Scala instance encapsulating classpath entries
 *                      for a given Scala version.
 * @param classpathOptions The classpath options for a compiler. This instance
 *                         returns false always only for Java compilers.
 * @param classLookup The mechanism to map class files to classpath entries.
 * @param searchClasspath The classpath used to look for binary dependencies.
 */
final class AnalyzingJavaCompiler private[sbt] (
    val javac: xsbti.compile.JavaCompiler,
    val classpath: Seq[File],
    val scalaInstance: xsbti.compile.ScalaInstance,
    val classpathOptions: ClasspathOptions,
    val classLookup: (String => Option[File]),
    val searchClasspath: Seq[File]
) extends JavaCompiler {

  // for compatibility
  def compile(
      sources: Seq[File],
      options: Seq[String],
      output: Output,
      callback: AnalysisCallback,
      incToolOptions: IncToolOptions,
      reporter: XReporter,
      log: XLogger,
      progressOpt: Option[CompileProgress]
  ): Unit = {
    compile(sources,
            options,
            output,
            finalJarOutput = None,
            callback,
            incToolOptions,
            reporter,
            log,
            progressOpt)
  }

  /**
   * Compile some java code using the current configured compiler.
   *
   * @param sources  The sources to compile
   * @param options  The options for the Java compiler
   * @param output   The output configuration for this compiler
   * @param finalJarOutput The output that will be used for straight to jar compilation.
   * @param callback  A callback to report discovered source/binary dependencies on.
   * @param incToolOptions The component that manages generated class files.
   * @param reporter  A reporter where semantic compiler failures can be reported.
   * @param log       A place where we can log debugging/error messages.
   * @param progressOpt An optional compilation progress reporter to report
   *                    back what files are currently under compilation.
   */
  def compile(
      sources: Seq[File],
      options: Seq[String],
      output: Output,
      finalJarOutput: Option[File],
      callback: AnalysisCallback,
      incToolOptions: IncToolOptions,
      reporter: XReporter,
      log: XLogger,
      progressOpt: Option[CompileProgress]
  ): Unit = {
    // Helper for finding the ancestor of two files
    @annotation.tailrec
    def ancestor(f1: File, f2: File): Boolean = {
      if (f2 eq null) false
      else if (f1 == f2) true
      else ancestor(f1, f2.getParentFile)
    }

    if (sources.nonEmpty) {
      // Make the classpath absolute for Java compilation
      val absClasspath = classpath.map(_.getAbsoluteFile)

      // Outline chunks of compiles so that .class files end up in right location
      val chunks: Map[Option[File], Seq[File]] = output match {
        case single: SingleOutput =>
          Map(Some(single.getOutputDirectory) -> sources)
        case multi: MultipleOutput =>
          sources.groupBy { src =>
            multi.getOutputGroups
              .find { out =>
                ancestor(out.getSourceDirectory, src)
              }
              .map(_.getOutputDirectory)
          }
      }

      // Report warnings about source files that have no output directory
      chunks.get(None) foreach { srcs =>
        val culpritPaths = srcs.map(_.getAbsolutePath).mkString(", ")
        log.error(InterfaceUtil.toSupplier(s"No output directory mapped for: $culpritPaths"))
      }

      // Memoize the known class files in the Javac output directory
      val memo = for ((Some(outputDirectory), srcs) <- chunks) yield {
        val classesFinder = PathFinder(outputDirectory) ** "*.class"
        (classesFinder, classesFinder.get, srcs)
      }

      // Record progress for java compilation
      val javaCompilationPhase = "Java compilation"
      progressOpt.map { progress =>
        progress.startUnit(javaCompilationPhase, "")
        progress.advance(0, 2)
      }

      timed(javaCompilationPhase, log) {
        val args = JavaCompiler.commandArguments(
          absClasspath,
          output,
          options,
          scalaInstance,
          classpathOptions
        )
        val javaSources = sources.sortBy(_.getAbsolutePath).toArray
        val success =
          javac.run(javaSources, args.toArray, incToolOptions, reporter, log)
        if (!success) {
          /* Assume that no Scalac problems are reported for a Javac-related
           * reporter. This relies on the incremental compiler will not run
           * Javac compilation if Scala compilation fails, which means that
           * the same reporter won't be used for `AnalyzingJavaCompiler`. */
          val msg = "javac returned non-zero exit code"
          throw new CompileFailed(args.toArray, msg, reporter.problems())
        }
      }

      /** Read the API information from [[Class]] to analyze dependencies. */
      def readAPI(source: File, classes: Seq[Class[_]]): Set[(String, String)] = {
        val (apis, mainClasses, inherits) = ClassToAPI.process(classes)
        apis.foreach(callback.api(source, _))
        mainClasses.foreach(callback.mainClass(source, _))
        inherits.map {
          case (from, to) => (from.getName, to.getName)
        }
      }

      // Record progress for java analysis
      val javaAnalysisPhase = "Java analysis"
      progressOpt.map { progress =>
        progress.startUnit(javaAnalysisPhase, "")
        progress.advance(1, 2)
      }

      // Construct class loader to analyze dependencies of generated class files
      val loader = ClasspathUtilities.toLoader(searchClasspath)

      timed(javaAnalysisPhase, log) {
        for ((classesFinder, oldClasses, srcs) <- memo) {
          val newClasses = Set(classesFinder.get: _*) -- oldClasses
          Analyze(newClasses.toSeq, srcs, log, output, finalJarOutput)(callback, loader, readAPI)
        }
      }

      // After using the classloader it should be closed. Otherwise it will keep the accessed
      // jars open. Especially, when zinc is compiling directly to jar, that jar will be locked
      // not allowing to change it in further compilation cycles (on Windows).
      // This also affects jars in the classpath that come from dependency resolution.
      loader match {
        case u: URLClassLoader => u.close()
        case _                 => ()
      }

      // Report that we reached the end
      progressOpt.foreach { progress =>
        progress.advance(2, 2)
      }
    }
  }

  /**
   * Compile some java code using the current configured compiler. This
   * implements a method from [[JavaCompiler]] that will **not** perform
   * incremental compilation of any way.
   *
   * @note Don't use if you want incremental compilation.
   *
   * @param sources  The sources to compile
   * @param options  The options for the Java compiler
   * @param incToolOptions The component that manages generated class files.
   * @param reporter  A reporter where semantic compiler failures can be reported.
   * @param log       A place where we can log debugging/error messages.
   */
  override def run(
      sources: Array[File],
      options: Array[String],
      incToolOptions: IncToolOptions,
      reporter: XReporter,
      log: XLogger
  ): Boolean = javac.run(sources, options, incToolOptions, reporter, log)

  /** Time how long it takes to run various compilation tasks. */
  private[this] def timed[T](label: String, log: Logger)(t: => T): T = {
    val start = System.nanoTime
    val result = t
    val elapsed = System.nanoTime - start
    log.debug(label + " took " + (elapsed / 1e9) + " s")
    result
  }
}
