package xsbti.compile.analysis;

import xsbti.Problem;

/**
 * Defines the compiler information for a given compilation unit (source file).
 */
public interface SourceInfo {
    /**
     * Returns the reported problems by the Java or Scala compiler.
     * <p>
     * Note that a reported problem is a problem that has been shown to the end user.
     *
     * @return The compiler reported problems.
     */
    public Problem[] getReportedProblems();

    /**
     * Returns the unreported problems by the Java or Scala compiler.
     * <p>
     * Note that an unreported problem is a problem that has not been shown to the end user.
     *
     * @return The compiler reported problems.
     */
    public Problem[] getUnreportedProblems();
}

