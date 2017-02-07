/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.AnalysisCallback;
import xsbti.Logger;
import xsbti.Maybe;
import xsbti.Reporter;
import java.io.File;

/**
* Interface to a Scala compiler.
*/
public interface ScalaCompiler
{
	/**
	 * The `ScalaInstance` used by this compiler.
	 */
	ScalaInstance scalaInstance();

	/**
	 * The `ClasspathOptions` used by this compiler.
	 */
	ClasspathOptions classpathOptions();

	/**
	 * Recompile the subset of `sources` impacted by `changes` and collect new APIs.
	 *
	 * @param sources  All the sources of the project.
	 * @param changes  The changes that have been detected at the previous step.
	 * @param callback The callback to which the extracted information should be reported.
	 * @param log      Logger to use for compilation.
	 * @param reporter The reporter to which errors and warnings should be reported during compilation.
	 * @param progress Where to report the file being currently compiled.
	 * @param compiler The actual compiler that will perform the compilation step.
	 */
	void compile(File[] sources, DependencyChanges changes, AnalysisCallback callback, Logger log, Reporter reporter, CompileProgress progress, CachedCompiler compiler);

	/**
	 * Recompile the subset of `sources` impacted by `changes` and collect new APIs.
	 *
	 * @param sources     All the sources of the project.
	 * @param changes     The changes that have been detected at the previous step.
	 * @param options     Arguments to give to the compiler (-Xfatal-warnings, ...).
	 * @param output      Location where generated class files should be put.
	 * @param callback    The callback to which the extracted information should be reported.
	 * @param reporter    The reporter to which errors and warnings should be reported during compilation.
	 * @param cache       The cache from where we retrieve the compiler to use.
	 * @param log         Logger to use for compilation.
	 * @param progressOpt The `CompileProgress` to use to report the file being compile.
	 */
	void compile(File[] sources, DependencyChanges changes, String[] options, Output output, AnalysisCallback callback, Reporter reporter, GlobalsCache cache, Logger log, Maybe<CompileProgress> progressOpt);
}
