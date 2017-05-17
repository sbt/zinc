package xsbti.compile;

import java.io.File;
import java.io.Serializable;

/**
 * Define the interface of a group of outputs.
 */
public interface OutputGroup extends Serializable {
    /**
     * Return the directory where source files are stored for this group.
     * <p>
     * Note that source directories should uniquely identify the group
     * for a certain source file.
     */
    public File sourceDirectory();

    /**
     * Return the directory where class files should be generated.
     * <p>
     * Incremental compilation manages the class files in this directory, so
     * don't play with this directory out of the Zinc API. Zinc already takes
     * care of deleting classes before every compilation run.
     * <p>
     * This directory must be exclusively used for one set of sources.
     */
    public File outputDirectory();
}
