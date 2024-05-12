/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbti.compile;

import java.io.File;
import java.nio.file.Path;
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
     *
     * @deprecated use {@link #getSourceDirectoryAsPath()} instead.
     */
    @Deprecated
    public File getSourceDirectory();

    /**
     * Return the directory where source files are stored for this group.
     * <p>
     * Note that source directories should uniquely identify the group
     * for a certain source file.
     */
    public default Path getSourceDirectoryAsPath() {
        return getSourceDirectory().toPath();
    }
 
    /**
     * Return the directory where class files should be generated.
     * <p>
     * Incremental compilation manages the class files in this directory, so
     * don't play with this directory out of the Zinc API. Zinc already takes
     * care of deleting classes before every compilation run.
     * <p>
     * This directory must be exclusively used for one set of sources.
     */
    @Deprecated
    public File getOutputDirectory();

    /**
     * Return the directory where class files should be generated.
     * <p>
     * Incremental compilation manages the class files in this directory, so
     * don't play with this directory out of the Zinc API. Zinc already takes
     * care of deleting classes before every compilation run.
     * <p>
     * This directory must be exclusively used for one set of sources.
     */
    public default Path getOutputDirectoryAsPath() {
        return getOutputDirectory().toPath();
    }
}

