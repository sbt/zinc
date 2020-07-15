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

/**
 * Defines helpers to create GlobalsCache.
 */
public interface CompilerCache {
    /**
     * Returns a compiler cache that returns a fresh cached Scala compiler.
     *
     * @return A default compiler cache.
     */
    public static GlobalsCache getDefault() {
        return fresh();
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
