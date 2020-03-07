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

/**
 * Defines an analysis file that contains information about every incremental compile.
 *
 * This information can be persisted using an AnalysisStore.
 */
public interface AnalysisContents {
    /**
     * Returns an instance of AnalysisContents backed up by the default implementation.
     *
     * @param analysis An instance of CompileAnalysis.
     * @param setup An instance of MiniSetup.
     * @return An instance of AnalysisContents.
     */
    static AnalysisContents create(CompileAnalysis analysis, MiniSetup setup) {
        return CompileResult.of(analysis, setup, false);
    }

    /**
     * @return An instance of CompileAnalysis.
     */
    CompileAnalysis getAnalysis();

    /**
     * @return An instance of MiniSetup.
     */
    MiniSetup getMiniSetup();
}
