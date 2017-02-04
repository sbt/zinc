package sbt
package internal
package inc
package javac

import java.io.File

import sbt._
import sbt.internal.inc.classfile.Analyze
import sbt.internal.inc.classpath.ClasspathUtilities
import xsbti.compile._
import xsbti.{ AnalysisCallback, Reporter }
import sbt.io.PathFinder

import sbt.util.Logger
import xsbti.compile.ClassFileManager

/**
 * This is a java compiler which will also report any discovered source dependencies/apis out via
 * an analysis callback.
 *
 * @param searchClasspath Differes from classpath in that we look up binary dependencies via this classpath.
 * @param classLookup A mechanism by which we can figure out if a JAR contains a classfile.
 */
final class AnalyzingJavaCompiler private[sbt] (
  val javac: xsbti.compile.JavaCompiler,
  val classpath: Seq[File],
  val scalaInstance: xsbti.compile.ScalaInstance,
  val classpathOptions: ClasspathOptions,
  val classLookup: (String => Option[File]),
  val searchClasspath: Seq[File]
) {
  /**
   * Compile some java code using the current configured compiler.
   *
   * @param sources  The sources to compile
   * @param options  The options for the Java compiler
   * @param output   The output configuration for this compiler
   * @param callback  A callback to report discovered source/binary dependencies on.
   * @param classfileManager The component that manages generated class files.
   * @param reporter  A reporter where semantic compiler failures can be reported.
   * @param log       A place where we can log debugging/error messages.
   * @param progressOpt An optional compilation progress reporter.  Where we can report back what files we're currently compiling.
   */
  def compile(sources: Seq[File], options: Seq[String], output: Output, callback: AnalysisCallback,
    incToolOptions: IncToolOptions, reporter: Reporter, log: Logger, progressOpt: Option[CompileProgress]): Unit = {
    if (sources.nonEmpty) {
      val absClasspath = classpath.map(_.getAbsoluteFile)
      @annotation.tailrec def ancestor(f1: File, f2: File): Boolean =
        if (f2 eq null) false else if (f1 == f2) true else ancestor(f1, f2.getParentFile)
      // Here we outline "chunks" of compiles we need to run so that the .class files end up in the right
      // location for Java.
      val chunks: Map[Option[File], Seq[File]] = output match {
        case single: SingleOutput => Map(Some(single.outputDirectory) -> sources)
        case multi: MultipleOutput =>
          sources groupBy { src =>
            multi.outputGroups find { out => ancestor(out.sourceDirectory, src) } map (_.outputDirectory)
          }
      }
      // Report warnings about source files that have no output directory.
      chunks.get(None) foreach { srcs =>
        log.error("No output directory mapped for: " + srcs.map(_.getAbsolutePath).mkString(","))
      }
      // Here we try to memoize (cache) the known class files in the output directory.
      val memo = for ((Some(outputDirectory), srcs) <- chunks) yield {
        val classesFinder = PathFinder(outputDirectory) ** "*.class"
        (classesFinder, classesFinder.get, srcs)
      }
      // Construct a class-loader we'll use to load + analyze
      // dependencies from the Javac generated class files
      val loader = ClasspathUtilities.toLoader(searchClasspath)
      // TODO - Perhaps we just record task 0/2 here
      timed("Java compilation", log) {
        val args = JavaCompiler.commandArguments(absClasspath, output, options, scalaInstance, classpathOptions)
        val javaSources = sources.sortBy(_.getAbsolutePath).toArray
        val success = javac.run(javaSources, args.toArray, incToolOptions, reporter, log)
        if (!success) {
          // TODO - Will the reporter have problems from Scalac?  It appears like it does not, only from the most recent run.
          // This is because the incremental compiler will not run javac if scalac fails.
          throw new CompileFailed(args.toArray, "javac returned nonzero exit code", reporter.problems())
        }
      }
      // TODO - Perhaps we just record task 1/2 here

      // Reads the API information directly from the Class[_] object. Used when Analyzing dependencies.
      def readAPI(source: File, classes: Seq[Class[_]]): Set[(String, String)] = {
        val (apis, inherits) = ClassToAPI.process(classes)
        apis.foreach(callback.api(source, _))
        inherits.map {
          case (from: Class[_], to: Class[_]) => (from.getName, to.getName)
        }
      }
      // Runs the analysis portion of Javac.
      timed("Java analysis", log) {
        for ((classesFinder, oldClasses, srcs) <- memo) {
          val newClasses = Set(classesFinder.get: _*) -- oldClasses
          Analyze(newClasses.toSeq, srcs, log)(callback, loader, readAPI)
        }
      }
      // TODO - Perhaps we just record task 2/2 here
    }
  }
  /** Debugging method to time how long it takes to run various compilation tasks. */
  private[this] def timed[T](label: String, log: Logger)(t: => T): T = {
    val start = System.nanoTime
    val result = t
    val elapsed = System.nanoTime - start
    log.debug(label + " took " + (elapsed / 1e9) + " s")
    result
  }
}
