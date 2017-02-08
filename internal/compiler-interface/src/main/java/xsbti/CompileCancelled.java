/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti;

/**
 * An exception thrown when compilation cancellation has been requested during
 * Scala compiler run.
 */
public abstract class CompileCancelled extends RuntimeException {
	public abstract String[] arguments();
}
