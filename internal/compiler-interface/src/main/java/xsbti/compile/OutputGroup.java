package xsbti.compile;

import java.io.File;

/** Define the interface of a group of outputs. */
public interface OutputGroup {
    /**
     * Return the directory where source files are stored for this group.
     *
     * Note that source directories should uniquely identify the group
     * for a certain source file.
     */
    public File sourceDirectory();

    /**
     * Return the directory where class files should be generated.
     *
     * Incremental compilation manages the class files in this directory, so
     * don't play with this directory out of the Zinc API. Zinc already takes
     * care of deleting classes before every compilation run.
     *
     * This directory must be exclusively used for one set of sources.
     */
    public File outputDirectory();
}
