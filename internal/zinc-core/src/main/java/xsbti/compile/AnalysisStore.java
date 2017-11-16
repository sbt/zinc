/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.util.Optional;

/**
 * Defines a store interface that provides analysis read and write capabilities to users.
 *
 * The store is a backend-independent interface that allows implementors to decide how
 * the analysis stores are read and written before or after every incremental compile.
 *
 * The implementations of {@link AnalysisStore} live in interfaces extending this one.
 */
public interface AnalysisStore {
    /**
     * Returns an analysis store whose last contents are kept in-memory.
     *
     * There will be only one memory reference to an analysis files. Previous contents
     * will be discarded as {@link AnalysisStore#set(AnalysisContents)} or
     * {@link AnalysisStore#get()} is used.
     *
     * @param analysisStore The underlying analysis store that knows how to read/write contents.
     * @return An instance of a cached {@link AnalysisStore}.
     */
    static AnalysisStore getCachedStore(AnalysisStore analysisStore) {
        return sbt.internal.inc.AnalysisStore.cached(analysisStore);
    }

    /**
     * Returns a synchronized analysis store that is thread-safe.
     *
     * Thread-safety is achieved by synchronizing in the object.
     *
     * @param analysisStore The underlying analysis store that knows how to read/write contents.
     * @return An instance of a thread-safe {@link AnalysisStore}.
     */
    static AnalysisStore getThreadSafeStore(AnalysisStore analysisStore) {
        return sbt.internal.inc.AnalysisStore.sync(analysisStore);
    }

    /**
     * Gets an {@link AnalysisContents} from the underlying store.
     *
     * The contents of the analysis file are necessary for subsequent incremental compiles
     * given that the analysis files contains information about the previous incremental
     * compile and lets the incremental compiler decide what needs or needs not to be recompiled.
     *
     * This method should be called before every incremental compile.
     *
     * @return An instance of an optional {@link AnalysisContents}, depending on whether if exists or not.
     */
    Optional<AnalysisContents> get();

    /**
     * Sets an {@link AnalysisContents} to the underlying store.
     *
     * The contents of the analysis file are necessary for subsequent incremental compiles
     * given that the analysis files contains information about the previous incremental
     * compile and lets the incremental compiler decide what needs or needs not to be recompiled.
     *
     * This method is called after every incremental compile.
     *
     * @return An instance of {@link AnalysisContents}.
     */
    void set(AnalysisContents analysisContents);
}
