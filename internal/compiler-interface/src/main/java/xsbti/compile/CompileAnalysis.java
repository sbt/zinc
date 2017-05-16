/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.Serializable;

/**
 * Represents the analysis interface of an incremental compilation.
 *
 * The analysis interface conforms the public API of the Analysis files that
 * contain information about the incremental compilation of a project.
 */
public interface CompileAnalysis extends Serializable {}
