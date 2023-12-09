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

/**
 * Extension to AnalysisCallback2
 * refer to AnalysisCallback2.java for explanation
 */
public interface AnalysisCallback3 extends AnalysisCallback2 {
    ReadSourceInfos getSourceInfos();
}
