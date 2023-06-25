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

/**
 * Defines helpers to create GlobalsCache.
 */
public interface CompilerCache {
    /**
     * Alias for {@link #fresh()}.
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
     * @deprecated Switch to {@link #fresh()} (or just avoid the caching infrastructure).
     */
    @Deprecated
    public static GlobalsCache createCacheFor(int maxInstances) {
        return fresh();
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
