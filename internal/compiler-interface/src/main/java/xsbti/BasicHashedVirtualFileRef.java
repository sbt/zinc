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

    protected BasicHashedVirtualFileRef(String id, String contentHashStr) {
        super(id);
        this.contentHashStr = contentHashStr;
    }

    public String contentHashStr() { return contentHashStr; }

    public String toString() { return id() + ">" + contentHashStr; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BasicHashedVirtualFileRef)) return false;
        BasicHashedVirtualFileRef that = (BasicHashedVirtualFileRef) o;
        return Objects.equals(id(), that.id()) && Objects.equals(contentHashStr, that.contentHashStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            "xsbti.BasicHashedVirtualFileRef",
            id(),
            contentHashStr
        ); }
}
