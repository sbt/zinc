/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

/**
 * Determines if a classpath entry contains a class.
 *
 * The corresponding classpath entry is not exposed by this interface. It's tied
 * to it by a specific class that implements this interface.
 */
public interface DefinesClass {
    /**
     * Returns true if the classpath entry contains the requested class.
     */
    boolean apply(String className);
}
