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
     * This includes problems found by most recent compilation run and by all prior compilation runs.
     * This does not include problems found by prior compilation runs that are no longer valid.
     * e.g. If A.scala previously has a problem, but no longer has a problem, it will not be included.
     */
    public abstract ReadSourceInfos sourceInfos();
}
