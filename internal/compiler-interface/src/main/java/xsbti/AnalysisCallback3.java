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

package xsbti;

import xsbti.compile.analysis.ReadSourceInfos;

import java.nio.file.Path;

/**
 * Extension to {@link AnalysisCallback2}.
 * Similar to {@link AnalysisCallback2}, it serves as compatibility layer for Scala compilers.
 */
public interface AnalysisCallback3 extends AnalysisCallback2 {
    VirtualFile toVirtualFile(Path path);

    ReadSourceInfos getSourceInfos();
}
