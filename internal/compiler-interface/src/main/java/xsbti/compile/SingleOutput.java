/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;

/**
 * Represent a single output directory where the Zinc incremental compiler
 * will store all the generated class files by Java and Scala sources.
 */
public interface SingleOutput extends Output {
	/**
	 * Return the directory where class files should be generated.
	 *
	 * Incremental compilation manages the class files in this directory, so
	 * don't play with this directory out of the Zinc API. Zinc already takes
	 * care of deleting classes before every compilation run.
	 *
	 * This directory must be exclusively used for one set of sources.
	 */
	File outputDirectory();
}