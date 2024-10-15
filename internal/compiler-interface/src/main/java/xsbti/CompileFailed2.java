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

public abstract class CompileFailed2 extends CompileFailed {
    /**
     * Returns SourceInfos containing problems for each file.
     * This includes problems found by most recent compilation run.
     */
    public abstract ReadSourceInfos sourceInfos();
}
