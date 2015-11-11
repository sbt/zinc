package sbt
package inc

import sbt.util.Logger
import internal.inc.Analysis

import sbt.internal.inc.{ AnalyzingCompiler, ClasspathOptions, CompileOutput, IC, JavaTool, LoggerReporter, ScalaInstance }
import sbt.internal.inc.javac.{ IncrementalCompilerJavaTools, JavaTools }
import sbt.internal.inc.Locate.DefinesClass
import xsbti.Position
import xsbti.compile.{ CompileOrder, GlobalsCache, IncOptions, CompileSetup, CompileAnalysis, CompileResult }
import xsbti.compile.PreviousResult
import java.io.File
import sbt.util.Logger.m2o

object IncrementalCompiler {
  def compile(in: Inputs, log: Logger, reporter: xsbti.Reporter): CompileResult =
    {
      import in.compilers._
      import in.config._
      import in.incSetup._
      // Here is some trickery to choose the more recent (reporter-using) java compiler rather
      // than the previously defined versions.
      // TODO - Remove this hackery in sbt 1.0.
      val javacChosen: xsbti.compile.JavaCompiler =
        in.compilers.javac.xsbtiCompiler // ).getOrElse(in.inputs.compilers.javac)
      // TODO - Why are we not using the IC interface???
      IC.incrementalCompile(scalac, javacChosen, sources, classpath, CompileOutput(classesDirectory), cache, None, options, javacOptions,
        in.previousResult.analysis, m2o(in.previousResult.setup), analysisMap, definesClass, reporter, order, skip, incOptions)(log)
    }
}

/** Inputs necessary to run the incremental compiler. */
final case class Inputs(compilers: Compilers, config: Options, incSetup: IncSetup, previousResult: PreviousResult)

/** The instances of Scalac/Javac used to compile the current project. */
final case class Compilers(scalac: AnalyzingCompiler, javac: IncrementalCompilerJavaTools)

final case class Options(classpath: Seq[File], sources: Seq[File], classesDirectory: File, options: Seq[String], javacOptions: Seq[String], maxErrors: Int, sourcePositionMapper: Position => Position, order: CompileOrder)

final case class IncSetup(analysisMap: File => Option[CompileAnalysis], definesClass: DefinesClass, skip: Boolean, cacheFile: File, cache: GlobalsCache, incOptions: IncOptions)
