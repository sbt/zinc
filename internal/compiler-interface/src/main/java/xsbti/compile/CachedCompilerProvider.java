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
 * Represent a provider that creates cached Scala compilers from a Scala instance.
 */
public interface CachedCompilerProvider {
	/** Return the Scala instance used to provide cached compilers. */
	ScalaInstance scalaInstance();

	/** Return a new cached compiler from the usual compiler input. */
	CachedCompiler newCachedCompiler(String[] arguments, Output output, Logger log, Reporter reporter);
}
