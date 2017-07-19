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
     * @return A valid representation for logical equality, not referential equality.
     */
    public byte[] getBytes();

    /**
     * Returns a unique identifier depending on the underlying data structures.
     *
     * @deprecated use {@link #getValueId()} instead.
     * @return A valid int representation for logical equality, not referential equality.
     */
    @Deprecated public int getValueId();

    /**
     * @return A string-based and recoverable representation of the underlying stamp.
     */
    public String writeStamp();

    /**
     * Get the hash of the file contents if the stamp supports it.
     *
     * @deprecated use {@link #getHash64()} instead.
     *
     * @return An optional hash of the file contents.
     */
    @Deprecated public Optional<String> getHash();

    /**
     * Get the last modified time (in milliseconds from Epoch) of a file if the stamp supports it.
     *
     * @return An optional last modified time.
     */
    public Optional<Long> getLastModified();

    /**
     * Get a 64-byte hash of a file if the stamp supports it.
     *
     * @return An optional 64-byte hash.
     */
    public Optional<Long> getHash64();
}
