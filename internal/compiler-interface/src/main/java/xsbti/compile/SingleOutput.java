/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbti.compile;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Represent a single output directory where the Zinc incremental compiler
 * will store all the generated class files by Java and Scala sources.
 */
public interface SingleOutput extends Output {
    /**
     * Return the **directory or jar** where class files should be generated
     * and written to. The method name is a misnamer since it can return a
     * jar file when straight-to-jar compilation is enabled.
     * <p>
     * Incremental compilation manages the class files in this file, so don't
     * play with this directory out of the Zinc API. Zinc already takes care
     * of deleting classes before every compilation run.
     * <p>
     * This file or directory must be exclusively used for one set of sources.
     *
     * @deprecated use {@link #getOutputDirectoryAsPath()} instead.
     */
    @Deprecated
    public File getOutputDirectory();

    /**
     * Return the **directory or jar** where class files should be generated
     * and written to. The method name is a misnamer since it can return a
     * jar file when straight-to-jar compilation is enabled.
     * <p>
     * Incremental compilation manages the class files in this file, so don't
     * play with this directory out of the Zinc API. Zinc already takes care
     * of deleting classes before every compilation run.
     * <p>
     * This file or directory must be exclusively used for one set of sources.
     */
    public default Path getOutputDirectoryAsPath() {
        return getOutputDirectory().toPath();
    }

    @Override
    public default Optional<File> getSingleOutput() {
        return Optional.of(getOutputDirectory());
    }

    @Override
    public default Optional<Path> getSingleOutputAsPath() {
        return Optional.of(getOutputDirectoryAsPath());
    }
    
    @Override
    public default Optional<OutputGroup[]> getMultipleOutput() {
        return Optional.empty();
    }
}

