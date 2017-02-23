/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.AnalysisCallback;
import xsbti.Logger;
import xsbti.Reporter;
import java.io.File;

/**
 * Define the interface of a cached Scala compiler that can be run.
 *
 * This cached compiler hides the implementation of a compiler by just
 * defining two operations: {@link #commandArguments(File[])} and
 * {@link #run(File[], DependencyChanges, AnalysisCallback, Logger, Reporter, CompileProgress)}.
 *
 */
public interface CachedCompiler {
	/**
	 * Return an array of arguments that represent a command-line like
	 * equivalent of a call to the Scala compiler, but without the command itself.
	 *
     * @param sources The source files that the compiler must compile.
	 *
	 * @return The array of arguments of the Scala compiler.
	 */
	String[] commandArguments(File[] sources);

	/**
	 * Run the cached Scala compiler with inputs of incremental compilation.
	 *
	 * @param sources The source files to be compiled.
	 * @param changes The changes that have occured since last compilation.
	 * @param callback The callback injected by the incremental compiler.
	 * @param logger The logger of the incremental compilation.
	 * @param delegate The reporter that informs on the compiler's output.
	 * @param progress The compiler progress associated with a Scala compiler.
	 */
	void run(File[] sources, DependencyChanges changes, AnalysisCallback callback, Logger logger, Reporter delegate, CompileProgress progress);
}
