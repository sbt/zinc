/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

/**
 * Define an abstract interface that represents the output of the compilation.
 *
 * Inheritors are {@link SingleOutput} with a global output directory and
 * {@link MultipleOutput} that specifies the output directory per source file.
 */
public interface Output {}
