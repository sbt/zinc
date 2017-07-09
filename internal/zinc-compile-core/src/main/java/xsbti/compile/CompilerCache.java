/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

/**
 * Defines helpers to create {@link GlobalsCache}.
 */
public interface CompilerCache {
    /**
     * Returns a compiler cache that manages up until <b>5</b> cached Scala compilers,
     * where 5 is the default number.
     *
     * @return A default compiler cache.
     */
    public static GlobalsCache getDefault() {
        return createCacheFor(5);
    }

    /**
     * Returns a compiler cache that manages up until <code>maxInstances</code> of
     * cached Scala compilers.
     *
     * @param maxInstances Number of maximum cached Scala compilers.
     * @return A compiler cache for <code>maxInstances</code> compilers.
     */
    public static GlobalsCache createCacheFor(int maxInstances) {
        return new sbt.internal.inc.CompilerCache(maxInstances);
    }

    /**
     * Returns a compiler cache that returns a fresh cached Scala compiler.
     *
     * @return A compiler cache that always return a fresh cached Scala compiler.
     */
    public static GlobalsCache fresh() {
        return new sbt.internal.inc.FreshCompilerCache();
    }
}
