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
	/** Return an array of the existent output groups. */
	OutputGroup[] outputGroups();
}