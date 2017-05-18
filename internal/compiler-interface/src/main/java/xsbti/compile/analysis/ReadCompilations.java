/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile.analysis;

import java.io.Serializable;

/**
 * Defines an interface to read information about Zinc's incremental compilations.
 * <p>
 * This API is useful to check how many times Zinc has compiled a set of sources and
 * when that compilation took place. One can also use it to test Zinc's regressions.
 */
public interface ReadCompilations extends Serializable {
    /**
     * Returns an array of {@link Compilation compilation instances} that provide
     * information on Zinc's compilation runs.
     * <p>
     * Note that the array may be empty if Zinc has not compiled your project yet.
     *
     * @return An array of compilation information.
     */
    public Compilation[] getAllCompilations();
}
