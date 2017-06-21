/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti;

/**
 * Define an interface for a reporter of the compiler.
 *
 * The reporter exposes compilation errors coming from either Scala or
 * Java compilers. This includes messages with any level of severity:
 * from error, to warnings and infos.
 */
public interface Reporter {
	/** Reset logging and any accumulated error, warning, message or count. */
	void reset();

	/** Check whether the logger has received any error since last reset. */
	boolean hasErrors();

	/** Check whether the logger has received any warning since last reset. */
	boolean hasWarnings();

	/** Log a summary of the received output since the last reset. */
	void printSummary();

	/** Return a list of warnings and errors since the last reset. */
	Problem[] problems();

	/** Log a message at a concrete position and with a concrete severity. */
	void log(Problem problem);

	/** Report a comment. */
	void comment(Position pos, String msg);
}
