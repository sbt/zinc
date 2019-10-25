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

import xsbti.VirtualFileRef;

/**
 * Define the changes that can occur to the dependencies of a given compilation run.
 */
public interface DependencyChanges {
    /** Check whether there have been any change in the compilation dependencies. */
	boolean isEmpty();

    /**
     * Return the modified binaries since the last compilation run.
     * These modified binaries are either class files or jar files.
     */
	VirtualFileRef[] modifiedLibraries();

	/**
	 * Return the modified class names since the last compilation run.
     * These class names are mapped to sources and not binaries.
	 */
	String[] modifiedClasses();
}