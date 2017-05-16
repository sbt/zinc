/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile.analysis;

import xsbti.api.DependencyContext;

import java.io.File;

/**
 * A read-only interface to get the timestamps of the binaries, sources and compilation products.
 */
public interface ReadStamps {
    /**
     * Retrieves the stamp associated with a given class file.
     *
     * @param compilationProduct The product produced by the current compilation run of a source file.
     * @return The stamp for a class file.
     * @see xsbti.AnalysisCallback#generatedLocalClass(File, File)
     * @see xsbti.AnalysisCallback#generatedNonLocalClass(File, File, String, String)
     */
    public Stamp product(File compilationProduct);

    /**
     * Retrieves the stamp associated with a given internal source.
     *
     * @param internalSource The source file under compilation.
     * @return The stamp for the file.
     * @apiNote The internal source has to be a source under compilation.
     * @see xsbti.AnalysisCallback#startSource(File)
     */
    public Stamp source(File internalSource);

    /**
     * Retrieves the stamp associated with a binary dependency (class file).
     *
     * @param binaryFile A class file that represents an external or internal dependency.
     * @return The stamp for the file.
     * @see xsbti.AnalysisCallback#binaryDependency(File, String, String, File, DependencyContext)
     */
    public Stamp binary(File binaryFile);
}
