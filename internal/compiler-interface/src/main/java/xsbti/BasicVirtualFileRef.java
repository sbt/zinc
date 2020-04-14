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

import java.util.Arrays;
import java.util.ArrayList;

public class BasicVirtualFileRef implements VirtualFileRef {
    final private String parent;
    final private String name;

    protected BasicVirtualFileRef(String _id) {
        int idx = _id.lastIndexOf('/');
        if (idx >= 0) {
          parent = _id.substring(0, idx + 1);
        } else {
          parent = "";
        }
        name = _id.substring(idx + 1);
    }
  
    public String id() {
        return parent + name;
    }

    public String name() {
        return name;
    }

    public String[] names() {
        if (parent == null || parent == "") {
            return new String[] { name };
        }
        String[] parts = parent.split("/");
        String[] results = new String[parts.length + 1];
        int i = 0;
        for (i = 0; i < parts.length; i++) results[i] = parts[i];
        results[i] = name;
        return results;
    }

    public BasicVirtualFileRef withId(String id) {
        return new BasicVirtualFileRef(id);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof VirtualFileRef)) {
            return false;
        } else {
            VirtualFileRef o = (VirtualFileRef)obj;
            return this.id().equals(o.id());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.VirtualFileRef".hashCode()) + id().hashCode());
    }
    public String toString() {
        return id();
    }
}
