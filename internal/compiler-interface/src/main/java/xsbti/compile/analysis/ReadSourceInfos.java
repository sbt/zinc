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

package xsbti.compile.analysis;

import xsbti.VirtualFileRef;
import java.util.Map;

/**
 * Defines a read-only interface to get compiler information mapped to a source file.
 */
public interface ReadSourceInfos {
    /**
     * Returns the {@link SourceInfo sourceInfo} associated with a source file.
     *
     * @param sourceFile The source info associated with a source file.
     * @return A {@link SourceInfo sourceInfo}.
     */
    public SourceInfo get(VirtualFileRef sourceFile);

    /**
     * Returns a map of all source files with their corresponding source infos.
     *
     * @return A map of source files to source infos.
     * @see SourceInfo
     */
    public Map<VirtualFileRef, SourceInfo> getAllSourceInfos();
}
