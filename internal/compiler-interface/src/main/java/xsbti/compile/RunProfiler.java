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

import xsbti.VirtualFileRef;

public interface RunProfiler {
    void timeCompilation(long startNanos, long durationNanos);
    void registerInitial(InitialChanges changes);
    void registerEvent(String kind, String[] inputs, String[] outputs, String reason);
    void registerCycle(
            String[] invalidatedClasses,
            String[] invalidatedPackageObjects,
            VirtualFileRef[] initialSources,
            VirtualFileRef[] invalidatedSources,
            String[] recompiledClasses,
            APIChange[] changesAfterRecompilation,
            String[] nextInvalidations,
            boolean shouldCompileIncrementally
    );
    void registerRun();

    enum EMPTY implements RunProfiler {
        INSTANCE;

        @Override public void timeCompilation(long startNanos, long durationNanos) {}
        @Override public void registerInitial(InitialChanges changes) {}
        @Override public void registerEvent(String kind, String[] inputs, String[] outputs, String reason) {}
        @Override public void registerCycle(
                String[] invalidatedClasses,
                String[] invalidatedPackageObjects,
                VirtualFileRef[] initialSources,
                VirtualFileRef[] invalidatedSources,
                String[] recompiledClasses,
                APIChange[] changesAfterRecompilation,
                String[] nextInvalidations,
                boolean shouldCompileIncrementally
        ) {}
        @Override public void registerRun() {}
    }

    interface DelegatingRunProfiler extends RunProfiler {
        RunProfiler profiler();

        default void timeCompilation(long startNanos, long durationNanos) {
            profiler().timeCompilation(startNanos, durationNanos);
        }

        default void registerInitial(InitialChanges changes) {
            profiler().registerInitial(changes);
        }

        default void registerEvent(String kind, String[] inputs, String[] outputs, String reason) {
            profiler().registerEvent(kind, inputs, outputs, reason);
        }

        default public void registerCycle(
                String[] invalidatedClasses,
                String[] invalidatedPackageObjects,
                VirtualFileRef[] initialSources,
                VirtualFileRef[] invalidatedSources,
                String[] recompiledClasses,
                APIChange[] changesAfterRecompilation,
                String[] nextInvalidations,
                boolean shouldCompileIncrementally
        ) {
            profiler().registerCycle(
                    invalidatedClasses,
                    invalidatedPackageObjects,
                    initialSources,
                    invalidatedSources,
                    recompiledClasses,
                    changesAfterRecompilation,
                    nextInvalidations,
                    shouldCompileIncrementally
            );
        }

        default void registerRun() {
            profiler().registerRun();
        }
    }
}
