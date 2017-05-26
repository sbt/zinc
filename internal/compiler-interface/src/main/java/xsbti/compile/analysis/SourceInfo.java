/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile.analysis;

import xsbti.Problem;

/**
 * Defines the compiler information for a given compilation unit (source file).
 */
public interface SourceInfo {
    /**
     * Returns the reported problems by the Java or Scala compiler.
     * <p>
     * Note that a reported problem is a problem that has been shown to the end user.
     *
     * @return The compiler reported problems.
     */
    public Problem[] getReportedProblems();

    /**
     * Returns the unreported problems by the Java or Scala compiler.
     * <p>
     * Note that an unreported problem is a problem that has not been shown to the end user.
     *
     * @return The compiler reported problems.
     */
    public Problem[] getUnreportedProblems();

    /**
     * Returns the main classes found in this compilation unit.
     *
     * @return The full name of the main classes, like "foo.bar.Main"
     */
    public String[] getMainClasses();
}

