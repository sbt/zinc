/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

/**
 * An API for reporting when files are being compiled.
 *
 * This design is tied to the Scala compiler but it is used
 * by the Java compiler too, check the docs of the methods.
 */
public interface CompileProgress {

	/**
	 * Start the progress of a concrete phase for the path of a given
	 * compilation unit.
	 *
	 * @param phase The phase of the compiler being run.
	 * @param unitPath The path of the compilation unit.
	 */
	void startUnit(String phase, String unitPath);

	/**
	 * Advance the progress of the current unit.
	 *
	 * @param current The current progress.
	 * @param total The total of the progress that has to be achieved.
	 *
	 * @return Whether the progress has advanced or not since last report.
	 */
	boolean advance(int current, int total);
}