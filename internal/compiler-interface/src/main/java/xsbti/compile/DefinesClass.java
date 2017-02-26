/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

/**
 * Determine whether a classpath entry contains a class.
 *
 * The corresponding classpath entry is not exposed by this interface.
 */
public interface DefinesClass {
    /**
     * Return true if the classpath entry contains the requested class.
     *
     * @param className Binary name with JVM-like representation. Inner classes
     *                  are represented with '$'. For more information on the
     *                  binary name format, check section 13.1 of the Java
     *                  Language Specification.
     * @return True if <code>className</code> is found in the classpath entry.
     */
    boolean apply(String className);
}
