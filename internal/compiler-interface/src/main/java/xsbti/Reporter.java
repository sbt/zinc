/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti;

public interface Reporter
{
	/** Resets logging, including any accumulated errors, warnings, messages, and counts.*/
	void reset();
	/** Returns true if this logger has seen any errors since the last call to reset.*/
	boolean hasErrors();
	/** Returns true if this logger has seen any warnings since the last call to reset.*/
	boolean hasWarnings();
	/** Logs a summary of logging since the last reset.*/
	void printSummary();
	/** Returns a list of warnings and errors since the last reset.*/
	Problem[] problems();
	/** Logs a message.*/
	void log(Position pos, String msg, Severity sev);
	/** Reports a comment. */
	void comment(Position pos, String msg);
}
