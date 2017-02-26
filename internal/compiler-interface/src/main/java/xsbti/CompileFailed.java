/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti;

/**
 * Represent a failure occurred during compilation of Java or Scala sources.
 * This failure extends {@link RuntimeException} that you can catch at the use-site.
 */
public abstract class CompileFailed extends RuntimeException {
    /** Return an array of the initial arguments of the compiler. */
	public abstract String[] arguments();

	/** Return an array of {@link Problem} that reports on the found errors. */
	public abstract Problem[] problems();
}