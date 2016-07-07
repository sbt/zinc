package xsbti.compile;

import java.io.File;

/**
 * Defines extension point to proactively detect file changes.
 */
public interface FileWatch {
    /** Changes to class files. "since" parameter is used to indicate
     * the start time for the change.
     * When detection is not possible it returns nothing().
     */
    xsbti.Maybe<FileChanges> productChanges(long since);

    /** Changes to source files. "since" parameter is used to indicate
     * the start time for the change.
     * When detection is not possible it returns nothing().
     */
    xsbti.Maybe<FileChanges> sourceChanges(long since);

    /** Changes to binary files. "since" parameter is used to indicate
     * the start time for the change.
     * When detection is not possible it returns nothing().
     */
    xsbti.Maybe<FileChanges> binaryChanges(long since);
}
