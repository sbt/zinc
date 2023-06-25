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

import java.util.Set;

public interface InitialChanges {
    Changes<VirtualFileRef> getInternalSrc();
    Set<VirtualFileRef> getRemovedProducts();
    Set<VirtualFileRef> getLibraryDeps();
    APIChange[] getExternal();
}
