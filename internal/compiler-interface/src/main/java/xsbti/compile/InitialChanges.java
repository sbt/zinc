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

import xsbti.VirtualFileRef;

import java.util.Set;

public interface InitialChanges {
    Changes<VirtualFileRef> getInternalSrc();
    Set<VirtualFileRef> getRemovedProducts();
    Set<VirtualFileRef> getLibraryDeps();
    APIChange[] getExternal();
}
