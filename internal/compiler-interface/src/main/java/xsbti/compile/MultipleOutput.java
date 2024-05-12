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
import java.util.Optional;

/**
 * Represents a mapping of several outputs depending on the source directory.
 * <p>
 * This option is used only by the Scala compiler.
 */
public interface MultipleOutput extends Output {
    /**
     * Return an array of the existent output groups.
     * <p>
     * Incremental compilation manages the class files in these directories, so
     * don't play with them out of the Zinc API. Zinc already takes care of
     * deleting classes before every compilation run.
     */
    public OutputGroup[] getOutputGroups();

    @Deprecated
    @Override
    public default Optional<File> getSingleOutput() {
        return Optional.empty();
    }

    @Override
    public default Optional<Path> getSingleOutputAsPath() {
        return Optional.empty();
    }

    @Override
    public default Optional<OutputGroup[]> getMultipleOutput() {
        return Optional.of(getOutputGroups());
    }
}
