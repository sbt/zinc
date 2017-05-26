/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile.analysis;


import java.util.Optional;

/**
 * A stamp defines certain properties or information on files.
 * <p>
 * Stamp properties are available depending on its associated file.
 *
 * A stamp is empty when <code>getHash</code> and <code>getModified</code> return
 * an empty {@link Optional optional}. This value is returned for files that have
 * not been tracked by the incremental compiler.
 */
public interface Stamp {
    /**
     * Returns a unique identifier depending on the underlying data structures.
     *
     * @return A valid string-based representation for logical equality, not referential equality.
     */
    public int getValueId();

    /**
     * @return A string-based and recoverable representation of the underlying stamp.
     */
    public String writeStamp();

    /**
     * Get the hash of the file contents if the stamp supports it.
     *
     * @return An optional hash of the file contents.
     */
    public Optional<String> getHash();

    /**
     * Get the last modified time (in milliseconds from Epoch) of a file if the stamp supports it.
     *
     * @return An optional last modified time.
     */
    public Optional<Long> getLastModified();
}
