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
 * An interface which lets us know how to retrieve cached compiler instances form the current JVM.
 */
public interface GlobalsCache
{
	CachedCompiler apply(String[] args, Output output, boolean forceNew, CachedCompilerProvider provider, Logger log, Reporter reporter);
	void clear();
}
