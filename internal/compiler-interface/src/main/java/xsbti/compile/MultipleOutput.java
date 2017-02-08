/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;

public interface MultipleOutput extends Output {

	interface OutputGroup	{
		/** The directory where source files are stored for this group.
		 * Source directories should uniquely identify the group for a source file. */
		File sourceDirectory();

		/** The directory where class files should be generated.
		* Incremental compilation will manage the class files in this directory.
		* In particular, outdated class files will be deleted before compilation.
		* It is important that this directory is exclusively used for one set of sources. */
		File outputDirectory();
	}

	OutputGroup[] outputGroups();
}