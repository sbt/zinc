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

package xsbti;

import java.util.Objects;

public class BasicHashedVirtualFileRef
        extends BasicVirtualFileRef
        implements HashedVirtualFileRef {
    private final String contentHashStr;
    private final long sizeBytes;

    protected BasicHashedVirtualFileRef(String id, String contentHashStr, long sizeBytes) {
        super(id);
        this.contentHashStr = contentHashStr;
        this.sizeBytes = sizeBytes;
    }

    public String contentHashStr() { return contentHashStr; }

    public long sizeBytes() { return sizeBytes; }

    public String toString() { return id() + ">" + contentHashStr + "/" + Long.toString(sizeBytes); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BasicHashedVirtualFileRef)) return false;
        BasicHashedVirtualFileRef that = (BasicHashedVirtualFileRef) o;
        return Objects.equals(id(), that.id()) &&
            Objects.equals(contentHashStr, that.contentHashStr) &&
            (sizeBytes == that.sizeBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            "xsbti.BasicHashedVirtualFileRef",
            id(),
            contentHashStr,
            Long.valueOf(sizeBytes)
        ); }
}
