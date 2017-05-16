/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.compile.analysis.ReadStamps;

import java.io.Serializable;

/**
 * Represents the analysis interface of an incremental compilation.
 * <p>
 * The analysis interface conforms the public API of the Analysis files that
 * contain information about the incremental compilation of a project.
 */
public interface CompileAnalysis extends Serializable {
    /**
     * Returns a read-only stamps interface that allows users to map files to stamps.
     *
     * @return A read-only stamps interface to query for stamps.
     * @see xsbti.compile.analysis.Stamp
     */
    public ReadStamps readStamps();
}
