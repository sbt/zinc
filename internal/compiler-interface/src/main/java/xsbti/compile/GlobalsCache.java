/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;

/**
 * Define operations that let us retrieve cached compiler instances
 * for the current Java Virtual Machine.
 */
public interface GlobalsCache {

	/**
	 * Retrieve a {@link CachedCompiler}.
	 *
	 * @param args The arguments being passed on to the compiler.
	 * @param output The output instance where the compiler stored class files.
	 * @param forceNew Mark whether the previous cached compiler should be
	 *                 thrown away and a new one should be returned.
	 * @param provider The provider used for the cached compiler.
	 * @param logger The logger used to log compiler output.
	 * @param reporter The reporter used to report warnings and errors.
	 *
	 * @return An instance of the latest {@link CachedCompiler}.
	 */
	CachedCompiler apply(String[] args,
	                     Output output,
	                     boolean forceNew,
	                     CachedCompilerProvider provider,
	                     Logger logger,
	                     Reporter reporter);

	/**
	 * Clear the cache of {@link CachedCompiler cached compilers}.
	 */
	void clear();
}
