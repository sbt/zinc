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

public interface CachedCompiler
{
	/** Returns an array of arguments representing the nearest command line equivalent of a call to run but without the command name itself.*/
	String[] commandArguments(File[] sources);
	void run(File[] sources, DependencyChanges cpChanges, AnalysisCallback callback, Logger log, Reporter delegate, CompileProgress progress);
}
