/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;

/** Define the interface for several kind of output directories. */
public interface MultipleOutput extends Output {

	/** Define the interface of a group of outputs. */
	interface OutputGroup {
		/**
		 * Return the directory where source files are stored for this group.
         *
		 * Note that source directories should uniquely identify the group
		 * for a certain source file.
         */
		File sourceDirectory();

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

	/** Return an array of the existent output groups. */
	OutputGroup[] outputGroups();
}