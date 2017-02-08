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
 * Note; This is tied VERY SPECIFICALLY to scala.
 */
public interface CompileProgress {
	void startUnit(String phase, String unitPath);

	boolean advance(int current, int total);
}