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

public interface InvalidationProfiler {
    RunProfiler profileRun();

    enum EMPTY implements InvalidationProfiler {
        INSTANCE;

        @Override public RunProfiler profileRun() { return RunProfiler.EMPTY.INSTANCE; }
    }
}
