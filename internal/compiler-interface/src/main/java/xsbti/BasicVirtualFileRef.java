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

public class BasicVirtualFileRef implements VirtualFileRef {
    private final String id;
    private final String parent;
    private final String name;

    protected BasicVirtualFileRef(String _id) {
        this.id = _id.replace('\\', '/');
        int idx = id.lastIndexOf('/');
        parent = idx == -1 ? "" : id.substring(0, idx + 1);
        name = id.substring(idx + 1);
    }

    public String[] names() {
        if (Objects.equals(parent, "")) {
            return new String[] { name };
        }
        String[] parts = parent.split("/");
        String[] results = new String[parts.length + 1];
        System.arraycopy(parts, 0, results, 0, parts.length);
        results[parts.length] = name;
        return results;
    }

    public BasicVirtualFileRef withId(String id) { return new BasicVirtualFileRef(id); }

    public String id() { return id; } // keep the whole id as val sbt/zinc#768
    public String name() { return name; }
    public String toString() { return id; }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof VirtualFileRef)) {
            return false;
        } else {
            return Objects.equals(id(), ((VirtualFileRef) obj).id());
        }
    }

    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.VirtualFileRef".hashCode()) + id.hashCode());
    }
}
