/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti;

/**
 * Represent the cancellation of a compilation run. This failure extends
 * {@link RuntimeException} that you can catch at the use-site.
 */
public abstract class CompileCancelled extends RuntimeException {
    /** Return an array of the initial arguments of the compiler. */
	public abstract String[] arguments();
}
