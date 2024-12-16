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

import java.util.Optional;

/**
 * Defines a store interface that provides analysis read and write capabilities to users.
 *
 * The store is a backend-independent interface that allows implementors to decide how
 * the analysis stores are read and written before or after every incremental compile.
 *
 * The implementations of {@link AnalysisStore} live in interfaces extending this one.
 */
public interface AnalysisStore {
    /**
     * Returns an analysis store whose last contents are kept in-memory.
     *
     * There will be only one memory reference to an analysis files. Previous contents
     * will be discarded as {@link AnalysisStore#set(AnalysisContents)} or
     * {@link AnalysisStore#get()} is used.
     *
     * @param analysisStore The underlying analysis store that knows how to read/write contents.
     */
    static AnalysisStore getCachedStore(AnalysisStore analysisStore) {
        return new CachedAnalysisStore(analysisStore);
    }

    /**
     * Returns an analysis store whose last contents are kept in-memory.
     *
     * There will be only one memory reference to an analysis files. Previous contents
     * will be discarded as {@link AnalysisStore#set(AnalysisContents)} or
     * {@link AnalysisStore#get()} is used.
     *
     * @param analysisStore The underlying analysis store that knows how to read/write contents.
     */
    static AnalysisStore cached(AnalysisStore analysisStore) {
        return getCachedStore(analysisStore);
    }

    /**
     * Returns a synchronized analysis store that is thread-safe.
     *
     * Thread-safety is achieved by synchronizing in the object.
     *
     * @param analysisStore The underlying analysis store that knows how to read/write contents.
     */
    static AnalysisStore getThreadSafeStore(AnalysisStore analysisStore) {
        return new SyncedAnalysisStore(analysisStore);
    }

    /**
     * Returns a synchronized analysis store that is thread-safe.
     *
     * Thread-safety is achieved by synchronizing in the object.
     *
     * @param analysisStore The underlying analysis store that knows how to read/write contents.
     */
    static AnalysisStore sync(AnalysisStore analysisStore) {
        return getThreadSafeStore(analysisStore);
    }

    /**
     * Gets an {@link AnalysisContents} from the underlying store.
     *
     * The contents of the analysis file are necessary for subsequent incremental compiles
     * given that the analysis files contains information about the previous incremental
     * compile and lets the incremental compiler decide what needs or needs not to be recompiled.
     *
     * This method should be called before every incremental compile.
     *
     * @return An instance of an optional {@link AnalysisContents}, depending on whether if exists or not.
     */
    Optional<AnalysisContents> get();

    /**
     * Gets an {@link AnalysisContents} from the underlying store.
     *
     */
    AnalysisContents unsafeGet();

    /**
     * Sets an {@link AnalysisContents} to the underlying store.
     *
     * The contents of the analysis file are necessary for subsequent incremental compiles
     * given that the analysis files contains information about the previous incremental
     * compile and lets the incremental compiler decide what needs or needs not to be recompiled.
     *
     * This method is called after every incremental compile.
     */
    void set(AnalysisContents analysisContents);

    /**
     * Resets in memory cached {@link AnalysisContents}
     */
    default void clearCache() {}

    final class CachedAnalysisStore implements AnalysisStore {
        private AnalysisStore underlying;
        private Optional<AnalysisContents> lastStore = Optional.empty();
        
        CachedAnalysisStore(AnalysisStore underlying) {
            this.underlying = underlying;
        }

        public Optional<AnalysisContents> get() {
            if (!lastStore.isPresent()) {
                lastStore = underlying.get();
            }
            return lastStore;
        }
        public AnalysisContents unsafeGet() {
            return get().get();
        }
        public void set(AnalysisContents analysisContents) {
            underlying.set(analysisContents);
            lastStore = Optional.of(analysisContents);
        }

        public void clearCache() {
            lastStore = Optional.empty();
        }
    }

    final class SyncedAnalysisStore implements AnalysisStore {
        private AnalysisStore underlying;
        SyncedAnalysisStore(AnalysisStore underlying) {
            this.underlying = underlying;
        }
        public Optional<AnalysisContents> get() {
            synchronized(this) {
                return underlying.get();
            }
        }
        public AnalysisContents unsafeGet() {
            return get().get();
        }
        public void set(AnalysisContents analysisContents) {
            synchronized(this) {
                underlying.set(analysisContents);
            }
        }

        public void clearCache() {
            if (underlying instanceof CachedAnalysisStore) {
                underlying.clearCache();
            }
        }
    }
}
