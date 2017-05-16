package xsbti.compile.analysis;

import xsbti.Problem;

/**
 * Defines the compiler information for a given compilation unit (source file).
 */
public interface SourceInfo {
    /**
     * Returns the reported problems by the Java or Scala compiler.
     *
     * @return The compiler reported problems.
     * @apiNote A reported problem is a problem that has been shown to the end user.
     */
    public Problem[] getReportedProblems();

    /**
     * Returns the unreported problems by the Java or Scala compiler.
     *
     * @return The compiler reported problems.
     * @apiNote An unreported problem is a problem that has not been shown to the end user.
     */
    public Problem[] getUnreportedProblems();
}

