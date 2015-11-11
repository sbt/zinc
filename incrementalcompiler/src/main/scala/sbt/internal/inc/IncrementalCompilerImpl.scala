package sbt
package internal
package inc

import sbt.internal.inc.javac.{ IncrementalCompilerJavaTools, JavaTools }
import xsbti.{ Position, Logger }
import xsbti.compile.{ CompileOrder, GlobalsCache, IncOptions, CompileSetup, CompileAnalysis, CompileResult, CompileOptions }
import xsbti.compile.{ PreviousResult, Setup, Inputs, IncrementalCompiler }
import xsbti.compile.{ Compilers => XCompilers }
import java.io.File
import sbt.util.Logger.m2o

private[sbt] class IncrementalCompilerImpl extends IncrementalCompiler {
  import IncrementalCompilerImpl._

  override def compile(in: Inputs, log: Logger): CompileResult =
    {
      val cs = in.compilers()
      val config = in.options()
      val setup = in.setup()
      import cs._
      import config._
      import setup._
      // Here is some trickery to choose the more recent (reporter-using) java compiler rather
      // than the previously defined versions.
      // TODO - Remove this hackery in sbt 1.0.
      val javacChosen: xsbti.compile.JavaCompiler =
        in.compilers match {
          case Compilers(_, javac) => javac.xsbtiCompiler
        } // ).getOrElse(in.inputs.compilers.javac)
      val scalac = in.compilers match {
        case Compilers(scalac, _) => scalac
      }
      // TODO - Why are we not using the IC interface???
      IC.incrementalCompile(scalac, javacChosen, sources, classpath, CompileOutput(classesDirectory), cache, None, scalacOptions, javacOptions,
        in.previousResult.analysis, m2o(in.previousResult.setup),
        { f => m2o(analysisMap()(f)) },
        { f => { s => definesClass()(f)(s) } },
        reporter, order, skip, incrementalCompilerOptions)(log)
    }
}

private[sbt] object IncrementalCompilerImpl {
  /** The instances of Scalac/Javac used to compile the current project. */
  final case class Compilers(scalac: AnalyzingCompiler, javac: IncrementalCompilerJavaTools) extends XCompilers
}
