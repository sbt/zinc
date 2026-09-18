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

/**
 * A source class name together with the namespace it lives in.
 *
 * Only source class names: binary names are a different name space and already
 * encode the distinction ("A" vs "A$").
 */
public final class ClassRef implements java.io.Serializable {
    public static ClassRef of(String name, NameKind kind) {
        return new ClassRef(name, kind);
    }

    private final String name;
    private final NameKind kind;

    private ClassRef(String name, NameKind kind) {
        super();
        this.name = name;
        this.kind = kind;
    }

    public String name() {
        return this.name;
    }

    public NameKind kind() {
        return this.kind;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ClassRef)) {
            return false;
        } else {
            ClassRef o = (ClassRef) obj;
            return this.name.equals(o.name()) && this.kind.equals(o.kind());
        }
    }

    public int hashCode() {
        return 37 * (37 * (37 * 17 + "xsbti.ClassRef".hashCode()) + name.hashCode()) + kind.hashCode();
    }

    public String toString() {
        return "ClassRef(" + name + ", " + kind + ")";
    }
}
