/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
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
	 * @param unitPath The path of the compilation unit. It will be empty
	 *                 when a Java compiler is reporting the progress.
	 */
	void startUnit(String phase, String unitPath);

	/**
	 * Advance the progress of the current unit.
	 *
	 * @param current The current progress.
	 * @param total The total of the progress that has to be achieved.
	 * @param prevPhase The name of the previous phase.
	 * @param nextPhase The name of the next phase.
	 *
	 * @return Whether the user has cancelled compilation or not.
	 */
	boolean advance(int current, int total, String prevPhase, String nextPhase);

	/**
	 * Called when early output is created mid-compilation, or
	 * was ready to be created but needed to bail out because macros were found.
	 *
	 * @param success True if output is written; false otherwise.
	 */
	void earlyOutputComplete(boolean success);
}
