/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import sbt.internal.inc.ConcreteAnalysisContents;

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
        return new ConcreteAnalysisContents(analysis, setup);
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
