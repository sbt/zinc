/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;

public interface SingleOutput extends Output {

	/** The directory where class files should be generated.
	* Incremental compilation will manage the class files in this directory.
	* In particular, outdated class files will be deleted before compilation.
	* It is important that this directory is exclusively used for one set of sources. */
	File outputDirectory();
}