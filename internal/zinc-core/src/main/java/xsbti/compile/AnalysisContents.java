package xsbti.compile;

/**
 * Defines an analysis file that contains information about every incremental compile.
 *
 * This information can be persisted using an {@link AnalysisStore}.
 */
public interface AnalysisContents {
    /**
     * @return An instance of {@link CompileAnalysis}.
     */
    CompileAnalysis getAnalysis();

    /**
     * @return An instance of {@link MiniSetup}.
     */
    MiniSetup getMiniSetup();
}
