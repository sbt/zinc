/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.Serializable;

/**
 * Represents the analysis interface of an incremental compilation.
 */
public abstract class CompileAnalysis implements Serializable {
    public CompileAnalysis() {
        super();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof CompileAnalysis)) {
            return false;
        } else {
            CompileAnalysis o = (CompileAnalysis) obj;
            return true;
        }
    }

    public String toString() {
        return "CompileAnalysis(" + ")";
    }
}
