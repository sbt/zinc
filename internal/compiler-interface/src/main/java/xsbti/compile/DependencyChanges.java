/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

	import java.io.File;

// only includes changes to dependencies outside of the project
public interface DependencyChanges
{
	boolean isEmpty();
	// class files or jar files
	File[] modifiedBinaries();
	// class names
	String[] modifiedClasses();
}