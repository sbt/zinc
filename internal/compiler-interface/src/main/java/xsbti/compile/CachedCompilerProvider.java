/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;

public interface CachedCompilerProvider
{
	ScalaInstance scalaInstance();
	CachedCompiler newCachedCompiler(String[] arguments, Output output, Logger log, Reporter reporter, boolean resident);
}