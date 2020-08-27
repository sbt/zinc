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
    private final String id; // keep the whole id as val sbt/zinc#768

    public BasicVirtualFileRef withId(String id) { return new BasicVirtualFileRef(id); }

    protected BasicVirtualFileRef(String id) { this.id = id.replace('\\', '/'); }

    public String id() { return id; }
    public String name() { return id.substring(id.lastIndexOf('/') + 1); }
    public String[] names() { return id.split("/"); }
    public String toString() { return id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BasicVirtualFileRef)) return false;
        BasicVirtualFileRef that = (BasicVirtualFileRef) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash("xsbti.BasicVirtualFileRef", id); }
}
