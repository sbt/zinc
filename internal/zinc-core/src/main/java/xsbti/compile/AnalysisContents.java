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
 * This information can be persisted using an {@link AnalysisStore}.
 */
public interface AnalysisContents {
    /**
     * Returns an instance of {@link AnalysisContents} backed up by the default implementation.
     *
     * @param analysis An instance of {@link CompileAnalysis}.
     * @param setup An instance of {@link MiniSetup}.
     * @return An instance of {@link AnalysisContents}.
     */
    static AnalysisContents create(CompileAnalysis analysis, MiniSetup setup) {
        return new ConcreteAnalysisContents(analysis, setup);
    }

    /**
     * @return An instance of {@link CompileAnalysis}.
     */
    CompileAnalysis getAnalysis();

    /**
     * @return An instance of {@link MiniSetup}.
     */
    MiniSetup getMiniSetup();
}
