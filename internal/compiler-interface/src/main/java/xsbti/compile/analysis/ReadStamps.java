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

package xsbti.compile.analysis;

import xsbti.VirtualFileRef;
import xsbti.VirtualFile;
import xsbti.api.DependencyContext;

import java.util.Map;

/**
 * A read-only interface to get the timestamps of the binaries, sources and compilation products.
 */
public interface ReadStamps {
    /**
     * Retrieves the stamp associated with a given class file.
     *
     * @param compilationProduct The product produced by the current compilation run of a source file.
     * @return The stamp for a class file.
     */
    public Stamp product(VirtualFileRef compilationProduct);

    /**
     * Retrieves the stamp associated with a given internal source.
     * <p>
     * Note that the internal source has to be a source under compilation.
     *
     * @param internalSource The source file under compilation.
     * @return The stamp for the file.
     */
    public Stamp source(VirtualFile internalSource);

    /**
     * Retrieves the stamp associated with a binary dependency (class file).
     *
     * @param libraryFile A class file that represents an external or internal dependency.
     * @return The stamp for the file.
     */
    public Stamp library(VirtualFileRef libraryFile);

    /**
     * Returns a map of all the stamps associated with binary files.
     *
     * @return A map of binary files to stamps.
     */
    public Map<VirtualFileRef, Stamp> getAllLibraryStamps();

    /**
     * Returns a map of all the stamps associated with source files.
     *
     * @return A map of source files to stamps.
     */
    public Map<VirtualFileRef, Stamp> getAllSourceStamps();

    /**
     * Returns a map of all the stamps associated with product files.
     * <p>
     * Note that the returned map can be empty if no compilation has happened yet.
     *
     * @return A map of product files to stamps.
     * (e.g. compile analysis is empty).
     */
    public Map<VirtualFileRef, Stamp> getAllProductStamps();

}
