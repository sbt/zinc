package xsbti.compile;

import java.util.Optional;

/**
 * Defines a store interface that provides analysis read and write capabilities to users.
 *
 * The store is a backend-independent interface that allows implementors to decide how
 * the analysis stores are read and written before or after every incremental compile.
 */
public interface AnalysisStore {
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
