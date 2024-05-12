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

import java.nio.file.Path;

public interface FileConverter {
    Path toPath(VirtualFileRef ref);

    default VirtualFile toVirtualFile(VirtualFileRef ref) {
        return ref instanceof VirtualFile ? ((VirtualFile) ref) : toVirtualFile(toPath(ref));
    }

    VirtualFile toVirtualFile(Path path);
}
