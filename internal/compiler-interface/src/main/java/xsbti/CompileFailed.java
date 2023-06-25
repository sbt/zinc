/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbti;

/**
 * Represent a failure occurred during compilation of Java or Scala sources.
 * This failure extends {@link RuntimeException} that you can catch at the use-site.
 */
public abstract class CompileFailed extends RuntimeException {

	public CompileFailed() {
		super();
	}

	public CompileFailed(final Throwable cause) {
		super(cause);
	}

    /** Return an array of the initial arguments of the compiler. */
	public abstract String[] arguments();

	/** Return an array of {@link Problem} that reports on the found errors. */
	public abstract Problem[] problems();
}